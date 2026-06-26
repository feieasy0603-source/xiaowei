package com.xiaowei.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaowei.common.BusinessException;
import com.xiaowei.domain.entity.Paper;
import com.xiaowei.domain.repository.PaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaperService {

    private final PaperRepository paperRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Map<String, Object>> listByUser(Long userId, int limit) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        int cap = Math.min(50, Math.max(1, limit));
        return paperRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .limit(cap)
                .map(this::toSummary)
                .toList();
    }

    private Map<String, Object> toSummary(Paper paper) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", paper.getId());
        m.put("title", paper.getTitle() != null && !paper.getTitle().isBlank() ? paper.getTitle() : "未命名草稿");
        m.put("productId", paper.getProductId());
        m.put("maxVisitedStep", paper.getMaxVisitedStep());
        m.put("updatedAt", paper.getUpdatedAt() != null ? paper.getUpdatedAt().toString() : null);
        m.put("version", paper.getVersion() != null ? paper.getVersion() : 1L);
        return m;
    }

    public Map<String, Object> get(String id, Long userId) {
        Paper paper = paperRepository.findById(id)
                .orElseThrow(() -> new BusinessException("论文不存在"));
        assertPaperAccess(paper, userId);
        return parseDraft(paper);
    }

    /** 系统内部（任务归档、订单履约）读取草稿，不做用户鉴权 */
    public Map<String, Object> getInternal(String id) {
        Paper paper = paperRepository.findById(id)
                .orElseThrow(() -> new BusinessException("论文不存在"));
        return parseDraft(paper);
    }

    @Transactional
    public Map<String, Object> create(Long userId, String productId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        Paper paper = new Paper();
        paper.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        paper.setUserId(userId);
        paper.setProductId(productId);
        paper.setTitle("");
        paper.setDraftJson(defaultDraftJson());
        paperRepository.save(paper);
        return parseDraft(paper);
    }

    @Transactional
    public Map<String, Object> save(String id, Map<String, Object> draft, Long userId) {
        return doSave(id, draft, userId, false);
    }

    /** 生成任务分段写入草稿：仅合并 preview，避免覆盖用户正在编辑的正文/提纲 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCheckpoint(String id, Map<String, Object> draft, Long userId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        Paper paper = paperRepository.findById(id)
                .orElseThrow(() -> new BusinessException("草稿不存在"));
        assertPaperAccess(paper, userId);
        Map<String, Object> merged = parseDraft(paper);
        if (draft.get("preview") != null) {
            merged.put("preview", draft.get("preview"));
        }
        doSave(id, merged, userId, true);
    }

    private Map<String, Object> doSave(String id, Map<String, Object> draft, Long userId, boolean skipVersionCheck) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        Paper paper = paperRepository.findById(id).orElseGet(() -> {
            Paper p = new Paper();
            p.setId(id);
            p.setUserId(userId);
            p.setTitle("");
            p.setDraftJson(defaultDraftJson());
            return p;
        });
        if (paper.getUserId() == null) {
            paper.setUserId(userId);
        } else if (!paper.getUserId().equals(userId)) {
            throw new BusinessException("无权操作");
        }
        boolean forceOverwrite = Boolean.TRUE.equals(draft.get("_forceOverwrite"));
        if (!skipVersionCheck && !forceOverwrite) {
            long expected = parseExpectedVersion(draft.get("version"));
            long current = paper.getVersion() != null ? paper.getVersion() : 1L;
            if (expected == 0 && current > 1) {
                throw new BusinessException(409, "草稿版本已过期，请刷新后重试");
            }
            if (expected > 0 && expected != current) {
                throw new BusinessException(409, "草稿已在其他设备更新，请刷新后重试");
            }
        }
        try {
            if (draft.get("title") != null) {
                paper.setTitle(String.valueOf(draft.get("title")));
            }
            int maxStep = paper.getMaxVisitedStep() != null ? paper.getMaxVisitedStep() : 0;
            if (draft.get("maxVisitedStep") != null) {
                maxStep = Math.max(maxStep, ((Number) draft.get("maxVisitedStep")).intValue());
            }
            paper.setMaxVisitedStep(maxStep);
            Object productId = draft.get("productId");
            if (productId != null && !String.valueOf(productId).isBlank()) {
                paper.setProductId(String.valueOf(productId));
            }
            paper.setDraftJson(objectMapper.writeValueAsString(stripClientOnlyFields(draft)));
            paper.setUpdatedAt(Instant.now());
            long ver = paper.getVersion() != null ? paper.getVersion() : 1L;
            paper.setVersion(ver + 1);
            paperRepository.save(paper);
            return parseDraft(paper);
        } catch (Exception e) {
            throw new BusinessException("保存失败");
        }
    }

    private long parseExpectedVersion(Object version) {
        if (version == null) return 0;
        if (version instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(version));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stripClientOnlyFields(Map<String, Object> draft) {
        Map<String, Object> copy = new HashMap<>(draft);
        copy.remove("_localUpdatedAt");
        copy.remove("updatedAt");
        copy.remove("productId");
        copy.remove("version");
        copy.remove("_forceOverwrite");
        return copy;
    }

    private Map<String, Object> parseDraft(Paper paper) {
        try {
            Map<String, Object> draft = objectMapper.readValue(paper.getDraftJson(), Map.class);
            draft.put("id", paper.getId());
            if (paper.getUpdatedAt() != null) {
                draft.put("updatedAt", paper.getUpdatedAt().toString());
            }
            if (paper.getProductId() != null) {
                draft.put("productId", paper.getProductId());
            }
            draft.put("version", paper.getVersion() != null ? paper.getVersion() : 1L);
            int colStep = paper.getMaxVisitedStep() != null ? paper.getMaxVisitedStep() : 0;
            Object jsonStep = draft.get("maxVisitedStep");
            int jsonStepVal = jsonStep instanceof Number n ? n.intValue() : 0;
            draft.put("maxVisitedStep", Math.max(colStep, jsonStepVal));
            return draft;
        } catch (Exception e) {
            throw new BusinessException("草稿解析失败");
        }
    }

    private void assertPaperAccess(Paper paper, Long userId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        if (paper.getUserId() != null && !paper.getUserId().equals(userId)) {
            throw new BusinessException("无权查看该草稿");
        }
    }

    private String defaultDraftJson() {
        return """
            {"id":"","title":"","meta":{"unit":"","position":"","paperType":"毕业论文","trainingStart":"","trainingEnd":"","wordCount":12000,"language":"zh","degree":"本科","category":"教育经管","schoolId":"","chartFormula":"无","formatNote":""},"researchNotes":"","literature":[],"outline":[],"outlineText":"","maxVisitedStep":0}
            """;
    }
}
