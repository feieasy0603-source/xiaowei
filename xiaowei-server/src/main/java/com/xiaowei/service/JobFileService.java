package com.xiaowei.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaowei.common.BusinessException;
import com.xiaowei.domain.entity.Job;
import com.xiaowei.domain.entity.JobFile;
import com.xiaowei.domain.repository.JobFileRepository;
import com.xiaowei.domain.repository.JobRepository;
import com.xiaowei.integration.files.DocxBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobFileService {

    private final JobFileRepository jobFileRepository;
    private final JobRepository jobRepository;
    private final FileStorageService fileStorageService;
    private final DocxBuilder docxBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Map<String, Object>> listByJob(Long jobId, Long userId) {
        assertJobAccess(jobId, userId);
        return jobFileRepository.findByJobIdOrderByCreatedAtDesc(jobId).stream()
                .map(this::toDto)
                .toList();
    }

    public JobFile requireFile(Long fileId) {
        return jobFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException("交付文件不存在"));
    }

    public void assertFileAccess(JobFile file, Long userId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (!file.getUserId().equals(userId)) {
            throw new BusinessException("无权下载");
        }
    }

    @Transactional
    public void archiveJobDeliverables(Job job, Map<String, Object> result) {
        if (job.getPaperId() != null && !job.getPaperId().isBlank()) {
            return;
        }
        if (jobFileRepository.existsByJobId(job.getId())) {
            return;
        }
        String title = extractTitle(job, result);
        String txt = buildTxtContent(title, result, job.getTaskType());
        String baseName = safeFileName(titleForTask(job.getTaskType(), title));

        Map<String, Object> txtStored = fileStorageService.saveText(baseName + ".txt", txt);
        saveRecord(job, "txt", (String) txtStored.get("fileName"),
                (String) txtStored.get("storageKey"), (Long) txtStored.get("size"));

        try {
            byte[] docxBytes = docxBuilder.buildFromText(baseName, txt);
            Map<String, Object> docStored = fileStorageService.saveBytes(baseName + ".docx", docxBytes);
            saveRecord(job, "docx", (String) docStored.get("fileName"),
                    (String) docStored.get("storageKey"), (Long) docStored.get("size"));
        } catch (Exception ex) {
            log.warn("快捷任务 DOCX 生成失败: {}", ex.getMessage());
        }
    }

    private void saveRecord(Job job, String type, String name, String key, Long size) {
        JobFile f = new JobFile();
        f.setJobId(job.getId());
        f.setUserId(job.getUserId());
        f.setFileType(type);
        f.setFileName(name);
        f.setStorageKey(key);
        f.setSizeBytes(size);
        jobFileRepository.save(f);
    }

    private String titleForTask(String taskType, String title) {
        return switch (taskType == null ? "" : taskType) {
            case "revise" -> "改稿结果";
            case "paraphrase" -> "降重结果";
            case "aigc_check" -> "AIGC检测报告";
            case "ppt_generate" -> "PPT大纲";
            case "file_translate" -> "翻译结果";
            case "data_analysis" -> "数据分析报告";
            default -> title;
        };
    }

    @SuppressWarnings("unchecked")
    private String buildTxtContent(String title, Map<String, Object> result, String taskType) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n\n");
        if (result.get("revisedText") != null) {
            sb.append(result.get("revisedText"));
            return sb.toString();
        }
        if (result.get("paraphrasedText") != null) {
            sb.append(result.get("paraphrasedText"));
            return sb.toString();
        }
        if (result.get("translatedText") != null) {
            sb.append(result.get("translatedText"));
            return sb.toString();
        }
        if (result.get("report") != null) {
            sb.append("【AIGC 检测报告】\n").append(result.get("report"));
            return sb.toString();
        }
        if (result.get("slidesOutline") != null) {
            sb.append("【PPT 大纲】\n").append(result.get("slidesOutline"));
            return sb.toString();
        }
        if (result.get("reportSummary") != null) {
            sb.append("【分析摘要】\n").append(result.get("reportSummary"));
            return sb.toString();
        }
        Object previewObj = result.get("preview");
        if (previewObj instanceof Map<?, ?> preview) {
            if (preview.get("abstractZh") != null) {
                sb.append("摘要\n").append(preview.get("abstractZh")).append("\n\n");
            }
            if (preview.get("sections") instanceof List<?> sections) {
                for (Object secObj : sections) {
                    if (secObj instanceof Map<?, ?> sec) {
                        sb.append(sec.get("title")).append('\n');
                        sb.append(sec.get("content")).append("\n\n");
                    }
                }
            }
            return sb.toString();
        }
        sb.append(result.getOrDefault("message", "AI 生成内容 (" + taskType + ")"));
        return sb.toString();
    }

    private String extractTitle(Job job, Map<String, Object> result) {
        try {
            if (job.getPayloadJson() != null) {
                Map<?, ?> payload = objectMapper.readValue(job.getPayloadJson(), Map.class);
                if (payload.get("title") != null) {
                    return String.valueOf(payload.get("title"));
                }
                if (payload.get("fileName") != null) {
                    return String.valueOf(payload.get("fileName"));
                }
            }
        } catch (Exception ignored) {
        }
        return String.valueOf(result.getOrDefault("message", "任务结果"));
    }

    private String safeFileName(String title) {
        String s = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return s.isBlank() ? "任务结果" : s;
    }

    private void assertJobAccess(Long jobId, Long userId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException("任务不存在"));
        if (userId == null || !userId.equals(job.getUserId())) {
            throw new BusinessException("无权查看");
        }
    }

    private Map<String, Object> toDto(JobFile f) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", f.getId());
        m.put("jobId", f.getJobId());
        m.put("fileType", f.getFileType());
        m.put("fileName", f.getFileName());
        m.put("sizeBytes", f.getSizeBytes());
        m.put("downloadUrl", "/api/files/job-delivery/" + f.getId() + "/download");
        return m;
    }
}
