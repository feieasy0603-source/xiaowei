package com.xiaowei.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaowei.domain.entity.Job;
import com.xiaowei.domain.entity.Paper;
import com.xiaowei.domain.entity.PaperFile;
import com.xiaowei.domain.entity.School;
import com.xiaowei.domain.repository.PaperFileRepository;
import com.xiaowei.domain.repository.PaperRepository;
import com.xiaowei.domain.repository.SchoolRepository;
import com.xiaowei.integration.files.DocxBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaperFileService {

    private final PaperFileRepository paperFileRepository;
    private final PaperRepository paperRepository;
    private final SchoolRepository schoolRepository;
    private final FileStorageService fileStorageService;
    private final DocxBuilder docxBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Map<String, Object>> listByPaper(String paperId) {
        return paperFileRepository.findByPaperIdOrderByCreatedAtDesc(paperId).stream()
                .map(this::toDto)
                .toList();
    }

    public Map<String, Object> getFile(Long fileId) {
        PaperFile f = paperFileRepository.findById(fileId)
                .orElseThrow(() -> new com.xiaowei.common.BusinessException("交付文件不存在"));
        return toDto(f);
    }

    public PaperFile requireFile(Long fileId) {
        return paperFileRepository.findById(fileId)
                .orElseThrow(() -> new com.xiaowei.common.BusinessException("交付文件不存在"));
    }

    @Transactional
    public void archiveJobDeliverables(Job job, Map<String, Object> result) {
        String paperId = job.getPaperId();
        if (paperId == null || paperId.isBlank()) {
            return;
        }
        if (!paperFileRepository.findByJobId(job.getId()).isEmpty()) {
            return;
        }

        String title = extractTitle(result, job);
        String txt = buildTxtContent(title, result, job.getTaskType());
        String baseName = safeFileName(title);
        String schoolLabel = resolveSchoolFormatLabel(paperId);

        Map<String, Object> txtStored = fileStorageService.saveText(baseName + ".txt", txt);
        saveRecord(paperId, job.getId(), "txt", (String) txtStored.get("fileName"),
                (String) txtStored.get("storageKey"), (Long) txtStored.get("size"));

        try {
            byte[] docxBytes;
            Object previewObj = result.get("preview");
            if (previewObj instanceof Map<?, ?> preview) {
                @SuppressWarnings("unchecked")
                byte[] built = docxBuilder.buildFromPreview(title, (Map<String, Object>) preview, schoolLabel);
                docxBytes = built;
            } else {
                docxBytes = docxBuilder.buildFromText(title, txt, schoolLabel);
            }
            Map<String, Object> docStored = fileStorageService.saveBytes(baseName + ".docx", docxBytes);
            saveRecord(paperId, job.getId(), "docx", (String) docStored.get("fileName"),
                    (String) docStored.get("storageKey"), (Long) docStored.get("size"));
        } catch (Exception ex) {
            log.warn("DOCX 生成失败，回退纯文本: {}", ex.getMessage());
            Map<String, Object> docStored = fileStorageService.saveText(baseName + ".docx", txt);
            saveRecord(paperId, job.getId(), "docx", (String) docStored.get("fileName"),
                    (String) docStored.get("storageKey"), (Long) docStored.get("size"));
        }
    }

    private void saveRecord(String paperId, Long jobId, String type, String name, String key, Long size) {
        PaperFile f = new PaperFile();
        f.setPaperId(paperId);
        f.setJobId(jobId);
        f.setFileType(type);
        f.setFileName(name);
        f.setStorageKey(key);
        f.setSizeBytes(size);
        paperFileRepository.save(f);
    }

    @SuppressWarnings("unchecked")
    String buildTxtContent(String title, Map<String, Object> result, String taskType) {
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
            if (preview.get("abstractEn") != null) {
                sb.append("ABSTRACT\n").append(preview.get("abstractEn")).append("\n\n");
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

    private String extractTitle(Map<String, Object> result, Job job) {
        try {
            if (job.getPayloadJson() != null) {
                Map<?, ?> payload = objectMapper.readValue(job.getPayloadJson(), Map.class);
                if (payload.get("title") != null) {
                    return String.valueOf(payload.get("title"));
                }
            }
        } catch (Exception ignored) {
        }
        return "论文";
    }

    private String safeFileName(String title) {
        String s = title.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return s.isBlank() ? "论文" : s;
    }

    @SuppressWarnings("unchecked")
    private String resolveSchoolFormatLabel(String paperId) {
        if (paperId == null || paperId.isBlank()) {
            return null;
        }
        return paperRepository.findById(paperId)
                .map(this::schoolLabelFromPaper)
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private String schoolLabelFromPaper(Paper paper) {
        try {
            Map<String, Object> draft = objectMapper.readValue(paper.getDraftJson(), Map.class);
            Object metaObj = draft.get("meta");
            if (!(metaObj instanceof Map<?, ?> meta)) {
                return null;
            }
            String schoolId = meta.get("schoolId") != null ? String.valueOf(meta.get("schoolId")) : "";
            if (schoolId.isBlank() || "other".equals(schoolId)) {
                return null;
            }
            return schoolRepository.findById(schoolId)
                    .filter(School::isEnabled)
                    .map(s -> "（" + s.getName() + " 论文格式）")
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> toDto(PaperFile f) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", f.getId());
        m.put("paperId", f.getPaperId());
        m.put("jobId", f.getJobId());
        m.put("fileType", f.getFileType());
        m.put("fileName", f.getFileName());
        m.put("sizeBytes", f.getSizeBytes());
        m.put("createdAt", f.getCreatedAt());
        m.put("downloadUrl", "/api/files/delivery/" + f.getId() + "/download");
        return m;
    }
}
