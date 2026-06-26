package com.xiaowei.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaowei.common.BusinessException;
import com.xiaowei.domain.entity.Job;
import com.xiaowei.domain.entity.Order;
import com.xiaowei.domain.entity.Paper;
import com.xiaowei.domain.entity.Product;
import com.xiaowei.domain.repository.JobRepository;
import com.xiaowei.domain.repository.PaperRepository;
import com.xiaowei.domain.repository.ProductRepository;
import com.xiaowei.integration.AiGateway;
import com.xiaowei.util.BusinessIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final ProductRepository productRepository;
    private final PaperRepository paperRepository;
    private final AiGateway aiGateway;
    private final PaperService paperService;
    private final PaperFileService paperFileService;
    private final JobFileService jobFileService;
    private final FileStorageService fileStorageService;
    private final VipQuotaService vipQuotaService;
    private final JobAsyncRunner jobAsyncRunner;
    private final ObjectProvider<JobService> jobServiceProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Transactional
    public Map<String, Object> createJob(Long userId, String productId, String paperId, Map<String, Object> payload) {
        return createJobInternal(userId, productId, paperId, null, payload);
    }

    @Transactional
    public Map<String, Object> createJobForOrder(Order order, Map<String, Object> payload) {
        return createJobInternal(
                order.getUserId(),
                order.getProductId(),
                order.getPaperId(),
                order.getId(),
                payload);
    }

    @Transactional
    protected Map<String, Object> createJobInternal(
            Long userId,
            String productId,
            String paperId,
            Long orderId,
            Map<String, Object> payload
    ) {
        if (userId == null) {
            throw new BusinessException("请先登录后再创建生成任务");
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException("产品不存在"));
        if (!Boolean.TRUE.equals(product.getEnabled())) {
            throw new BusinessException("产品已下架");
        }
        if (paperId != null && !paperId.isBlank()) {
            Paper paper = paperRepository.findById(paperId)
                    .orElseThrow(() -> new BusinessException("草稿不存在"));
            if (paper.getUserId() == null || !paper.getUserId().equals(userId)) {
                throw new BusinessException("无权使用该草稿");
            }
        }
        validateJobPayload(product.getTaskType(), payload);
        Job job = new Job();
        job.setJobNo(BusinessIdGenerator.jobNo());
        job.setUserId(userId);
        job.setProductId(productId);
        job.setPaperId(paperId);
        job.setOrderId(orderId);
        job.setTaskType(product.getTaskType());
        if (paperId != null) {
            payload.putIfAbsent("paperId", paperId);
        }
        if (orderId != null) {
            payload.putIfAbsent("orderId", orderId);
        }
        try {
            job.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            job.setPayloadJson("{}");
        }
        if (orderId == null && paperId != null && !paperId.isBlank()) {
            Optional<Job> latestPreview = jobRepository
                    .findFirstByPaperIdAndUserIdAndOrderIdIsNullOrderByCreatedAtDesc(paperId, userId);
            if (latestPreview.isPresent()) {
                Job existing = latestPreview.get();
                String st = existing.getStatus();
                if ("pending".equals(st) || "running".equals(st)) {
                    if (!"pending".equals(st)) {
                        scheduleJob(existing.getId());
                    }
                    return jobDto(existing);
                }
                if ("failed".equals(st) || "cancelled".equals(st)) {
                    return retryJobForUser(existing.getId(), userId);
                }
            }
        }
        jobRepository.save(job);
        if (orderId == null) {
            if (!vipQuotaService.hasFreeQuotaRemaining(userId, product.getTaskType())) {
                throw new BusinessException("今日免费次数已用完，请创建订单并支付后使用");
            }
            vipQuotaService.consumeFreeQuota(userId, product.getTaskType());
        }
        Long jobId = job.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.debug("事务已提交，调度生成任务 jobId={}", jobId);
                    jobAsyncRunner.run(jobId);
                }
            });
        } else {
            jobAsyncRunner.run(jobId);
        }
        return jobDto(job);
    }

    public Map<String, Object> getJob(Long id, Long userId) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new BusinessException("任务不存在"));
        assertJobOwner(job, userId);
        return jobDto(job);
    }

    private void validateJobPayload(String taskType, Map<String, Object> payload) {
        if ("paper_generate".equals(taskType) || "paper_preview".equals(taskType)) {
            if (payload == null) {
                throw new BusinessException("请填写论文标题或提纲后再生成");
            }
            String title = payload.get("title") != null ? String.valueOf(payload.get("title")).trim() : "";
            String outline = payload.get("outlineText") != null
                    ? String.valueOf(payload.get("outlineText")).trim()
                    : "";
            if (title.isBlank() && outline.isBlank()) {
                throw new BusinessException("请填写论文标题或提纲后再生成");
            }
        }
    }

    public void assertJobAccess(Long id, Long userId) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new BusinessException("任务不存在"));
        assertJobOwner(job, userId);
    }

    private void assertJobOwner(Job job, Long userId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (job.getUserId() != null && !job.getUserId().equals(userId)) {
            throw new BusinessException("无权查看该任务");
        }
    }

    /** 长文生成可能超过 2 分钟，SSE 保持 30 分钟；前端仍有轮询兜底 */
    public SseEmitter stream(Long id) {
        SseEmitter emitter = new SseEmitter(1_800_000L);
        emitters.put(id, emitter);
        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
        emitter.onError(e -> emitters.remove(id));
        jobRepository.findById(id).ifPresent(job -> {
            try {
                int p = job.getProgress() != null ? job.getProgress() : 0;
                emitter.send(SseEmitter.event().name("progress").data(p));
                String st = job.getStatus();
                if ("success".equals(st)) {
                    emitter.send(SseEmitter.event().name("done").data(String.valueOf(id)));
                    emitter.complete();
                } else if ("failed".equals(st)) {
                    emitter.send(SseEmitter.event().name("error").data(
                            job.getErrorMsg() != null ? job.getErrorMsg() : "生成失败"));
                    emitter.complete();
                } else if ("cancelled".equals(st)) {
                    emitter.send(SseEmitter.event().name("error").data("任务已取消"));
                    emitter.complete();
                }
            } catch (IOException ex) {
                emitters.remove(id);
            }
        });
        return emitter;
    }

    /** 独立事务提交进度，供轮询与 SSE 实时读取 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistAndBroadcastProgress(Long jobId, int progress) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        if ("cancelled".equals(job.getStatus())) return;
        if ("pending".equals(job.getStatus())) {
            job.setStatus("running");
        }
        job.setProgress(progress);
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
        sendProgress(jobId, progress);
    }

    @Transactional
    public void cancelJob(Long jobId) {
        cancelJobInternal(jobId, "管理员取消");
    }

    @Transactional
    public Map<String, Object> cancelJobForUser(Long jobId, Long userId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("任务不存在"));
        assertJobOwner(job, userId);
        cancelJobInternal(jobId, "用户取消");
        return jobDto(jobRepository.findById(jobId).orElse(job));
    }

    private void cancelJobInternal(Long jobId, String reason) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new BusinessException("任务不存在"));
        if (!List.of("pending", "running").contains(job.getStatus())) {
            throw new BusinessException("仅排队中或运行中任务可取消");
        }
        job.setStatus("cancelled");
        job.setErrorMsg(reason);
        job.setFinishedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);
        if (job.getOrderId() == null) {
            vipQuotaService.refundFreeQuotaOnce(job.getUserId(), job.getTaskType());
        }
        sendError(jobId, "任务已取消");
    }

    public Map<String, Object> getPreviewJobForPaper(String paperId, Long userId) {
        if (paperId == null || paperId.isBlank() || userId == null) {
            return Map.of();
        }
        return jobRepository.findFirstByPaperIdAndUserIdAndOrderIdIsNullOrderByCreatedAtDesc(paperId, userId)
                .map(this::jobDto)
                .orElse(Map.of());
    }

    private boolean isCancelled(Long jobId) {
        return jobRepository.findById(jobId)
                .map(j -> "cancelled".equals(j.getStatus()))
                .orElse(true);
    }

    public void scheduleJob(Long jobId) {
        jobAsyncRunner.run(jobId);
    }

    /** 用户重试自己的失败/已取消任务 */
    @Transactional
    public Map<String, Object> retryJobForUser(Long id, Long userId) {
        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new BusinessException("任务不存在"));
        assertJobOwner(job, userId);
        if ("running".equals(job.getStatus()) || "success".equals(job.getStatus())) {
            throw new BusinessException("运行中或已成功的任务无需重试");
        }
        if ("pending".equals(job.getStatus())) {
            scheduleJob(id);
            return jobDto(job);
        }
        if (!List.of("failed", "cancelled").contains(job.getStatus())) {
            throw new BusinessException("仅失败或已取消的任务可重试");
        }
        job.setStatus("pending");
        job.setProgress(0);
        job.setErrorMsg(null);
        job.setResultJson(null);
        job.setFinishedAt(null);
        job.setUpdatedAt(java.time.Instant.now());
        jobRepository.save(job);
        scheduleJob(id);
        return jobDto(job);
    }

    @Transactional
    public void executeJob(Long jobId) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        if ("cancelled".equals(job.getStatus())) return;
        try {
            job.setStatus("running");
            job.setUpdatedAt(Instant.now());
            jobRepository.save(job);
            JobService self = jobServiceProvider.getObject();
            self.persistAndBroadcastProgress(jobId, 8);

            Map<String, Object> payload = enrichPayload(job, parsePayload(job.getPayloadJson()));
            attachSectionCheckpoint(payload, job);
            self.persistAndBroadcastProgress(jobId, 12);

            Map<String, Object> result;
            if (Boolean.TRUE.equals(payload.get("skipAi"))) {
                result = buildReuseResult(job, payload);
                self.persistAndBroadcastProgress(jobId, 90);
            } else {
                result = aiGateway.processTask(job.getTaskType(), payload, p -> {
                    if (isCancelled(jobId)) return;
                    self.persistAndBroadcastProgress(jobId, p);
                });
            }

            String paperId = job.getPaperId();
            if (paperId == null && payload.get("paperId") != null) {
                paperId = String.valueOf(payload.get("paperId"));
            }
            if (paperId != null && result.containsKey("preview")) {
                Map<String, Object> draft = paperService.getInternal(paperId);
                draft.put("preview", result.get("preview"));
                paperService.saveCheckpoint(paperId, draft, job.getUserId());
            }

            paperFileService.archiveJobDeliverables(job, result);
            jobFileService.archiveJobDeliverables(job, result);

            job.setResultJson(objectMapper.writeValueAsString(compactJobResult(result, paperId)));
            job.setStatus("success");
            job.setProgress(100);
            job.setFinishedAt(Instant.now());
            job.setUpdatedAt(Instant.now());
            jobRepository.save(job);

            sendDone(jobId);
        } catch (Exception e) {
            job.setStatus("failed");
            job.setErrorMsg(e.getMessage());
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
            if (job.getOrderId() == null) {
                vipQuotaService.refundFreeQuotaOnce(job.getUserId(), job.getTaskType());
            }
            sendError(jobId, e.getMessage());
        }
    }

  /** 每写完若干节就把已生成正文写入草稿，避免任务未完成时页面只看到前几章 */
    @SuppressWarnings("unchecked")
    private void attachSectionCheckpoint(Map<String, Object> payload, Job job) {
        String paperId = job.getPaperId();
        if (paperId == null || paperId.isBlank()) {
            Object pid = payload.get("paperId");
            if (pid != null) {
                paperId = String.valueOf(pid);
            }
        }
        if (paperId == null || paperId.isBlank()) {
            return;
        }
        final String pid = paperId;
        final Long userId = job.getUserId();
        payload.put("_sectionCheckpoint", (java.util.function.Consumer<Map<String, Object>>) snap -> {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> secs =
                        snap.get("sections") instanceof List<?> list ? (List<Map<String, String>>) list : List.of();
                int done = snap.get("done") instanceof Number n ? n.intValue() : secs.size();
                int total = snap.get("total") instanceof Number n ? n.intValue() : secs.size();
                Map<String, Object> draft = paperService.getInternal(pid);
                Map<String, Object> preview = new LinkedHashMap<>();
                Object old = draft.get("preview");
                if (old instanceof Map<?, ?> om) {
                    if (om.get("abstractZh") != null) {
                        preview.put("abstractZh", om.get("abstractZh"));
                    }
                    if (om.get("abstractEn") != null) {
                        preview.put("abstractEn", om.get("abstractEn"));
                    }
                }
                if (!preview.containsKey("abstractZh")) {
                    preview.put("abstractZh", "【生成中】已完成 " + done + "/" + total + " 节，请稍候…");
                }
                if (!preview.containsKey("abstractEn")) {
                    preview.put("abstractEn", "Generation in progress (" + done + "/" + total + ")…");
                }
                preview.put("sections", secs);
                preview.put("generationProgress", Map.of("done", done, "total", total));
                draft.put("preview", preview);
                paperService.saveCheckpoint(pid, draft, userId);
            } catch (Exception ex) {
                log.warn("分段保存草稿失败: {}", ex.getMessage());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> enrichPayload(Job job, Map<String, Object> payload) {
        if (job.getPaperId() == null || job.getPaperId().isBlank()) {
            attachUploadedFileContent(payload);
            return payload;
        }
        try {
            Map<String, Object> draft = paperService.getInternal(job.getPaperId());
            PaperDraftHelper.mergeDraftIntoPayload(draft, payload);
            payload.putIfAbsent("paperId", job.getPaperId());
        } catch (Exception ignored) {
            /* 草稿不可用时沿用请求体 */
        }
        attachUploadedFileContent(payload);
        return payload;
    }

    private void attachUploadedFileContent(Map<String, Object> payload) {
        Object fileId = payload.get("fileId");
        if (fileId != null && !String.valueOf(fileId).isBlank()) {
            try {
                String text = fileStorageService.readTextPreview(String.valueOf(fileId));
                if (text != null && !text.isBlank()) {
                    payload.putIfAbsent("content", text);
                }
            } catch (Exception ex) {
                log.warn("读取上传文件失败: {}", ex.getMessage());
            }
        }
        Object fileId2 = payload.get("fileId2");
        if (fileId2 != null && !String.valueOf(fileId2).isBlank()) {
            try {
                String text2 = fileStorageService.readTextPreview(String.valueOf(fileId2));
                if (text2 != null && !text2.isBlank()) {
                    payload.putIfAbsent("content2", text2);
                }
            } catch (Exception ex) {
                log.warn("读取第二份上传文件失败: {}", ex.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildReuseResult(Job job, Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        result.put("taskType", job.getTaskType());
        result.put("message", "已复用预览生成结果，正在打包交付文件");
        result.put("reusedPreview", true);
        String paperId = job.getPaperId();
        if (paperId == null && payload.get("paperId") != null) {
            paperId = String.valueOf(payload.get("paperId"));
        }
        if (paperId != null) {
            try {
                Map<String, Object> draft = paperService.getInternal(paperId);
                Object preview = draft.get("preview");
                if (preview != null) {
                    result.put("preview", preview);
                }
            } catch (Exception ex) {
                log.warn("复用预览失败: {}", ex.getMessage());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void sendProgress(Long jobId, int progress) {
        SseEmitter emitter = emitters.get(jobId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("progress").data(progress));
            } catch (IOException ignored) {
            }
        }
    }

    /** 任务表仅存摘要；完整 preview 在 papers.draft_json，SSE 只通知完成避免超大 payload */
    private Map<String, Object> compactJobResult(Map<String, Object> result, String paperId) {
        Map<String, Object> slim = new HashMap<>();
        slim.put("taskType", result.get("taskType"));
        slim.put("message", result.get("message"));
        slim.put("model", result.get("model"));
        slim.put("provider", result.get("provider"));
        slim.put("mock", result.get("mock"));
        if (paperId != null) {
            slim.put("paperId", paperId);
        }
        if (result.containsKey("preview")) {
            slim.put("hasPreview", true);
        }
        copyIfPresent(result, slim, "revisedText");
        copyIfPresent(result, slim, "paraphrasedText");
        copyIfPresent(result, slim, "translatedText");
        copyIfPresent(result, slim, "report");
        copyIfPresent(result, slim, "slidesOutline");
        copyIfPresent(result, slim, "reportSummary");
        copyIfPresent(result, slim, "slides");
        return slim;
    }

    private void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String key) {
        if (from.get(key) != null) {
            to.put(key, from.get(key));
        }
    }

    private void sendDone(Long jobId) {
        SseEmitter emitter = emitters.get(jobId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("done").data(String.valueOf(jobId)));
                emitter.complete();
            } catch (IOException ignored) {
            }
            emitters.remove(jobId);
        }
    }

    private void sendError(Long jobId, String msg) {
        SseEmitter emitter = emitters.get(jobId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("error").data(msg));
                emitter.complete();
            } catch (IOException ignored) {
            }
            emitters.remove(jobId);
        }
    }

    private Map<String, Object> jobDto(Job job) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", job.getId());
        m.put("jobNo", job.getJobNo());
        m.put("productId", job.getProductId());
        m.put("paperId", job.getPaperId());
        m.put("orderId", job.getOrderId());
        m.put("taskType", job.getTaskType());
        m.put("status", job.getStatus());
        m.put("progress", job.getProgress());
        m.put("resultJson", job.getResultJson());
        m.put("errorMsg", job.getErrorMsg());
        return m;
    }
}
