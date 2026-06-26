package com.xiaowei.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaowei.integration.ai.LlmChatResult;
import com.xiaowei.integration.ai.LlmEndpoint;
import com.xiaowei.integration.ai.OpenAiCompatibleClient;
import com.xiaowei.integration.ai.PaperPreviewGenerator;
import com.xiaowei.service.AiConfigService;
import com.xiaowei.service.AiModelHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

@Component
@RequiredArgsConstructor
public class AiGateway {

    private final AiConfigService aiConfigService;
    private final AiModelHealthService aiModelHealthService;
    private final OpenAiCompatibleClient llmClient;
    private final PaperPreviewGenerator paperPreviewGenerator;
    private final ObjectMapper objectMapper;

    public String polishTitle(String title) {
        if (aiConfigService.isMock()) {
            return "【润色】" + title;
        }
        return aiConfigService.withLlmLimit("polish", () -> llmClient.chat(
                "polish",
                "你是学术论文标题润色助手，只输出润色后的标题，不要解释。",
                "请润色以下论文标题：\n" + title
        ));
    }

    public List<String> recommendTitles(String keyword, String productId) {
        String k = keyword == null ? "" : keyword.trim();
        if (k.length() < 2) {
            return List.of();
        }
        if (aiConfigService.isMock()) {
            return mockRecommendTitles(k);
        }
        String text = aiConfigService.withLlmLimit("recommend_titles", () -> llmClient.chat(
                "recommend_titles",
                "你是学术论文选题助手。根据用户关键词输出 JSON 字符串数组，3到4条完整论文标题，不要 markdown 和解释。",
                "关键词：" + k + "\n产品：" + (productId == null || productId.isBlank() ? "通用" : productId)
        ));
        return parseTitleList(text, k);
    }

    public List<Map<String, Object>> searchOutline(String title, String degree) {
        if (aiConfigService.isMock()) {
            return mockOutlineList(title, degree);
        }
        String text = aiConfigService.withLlmLimit("outline_search", () -> llmClient.chat(
                "outline_search",
                "你是论文提纲推荐助手。输出 JSON 数组，每项含 title、degree、depth(2或3)，不要 markdown。",
                "题目：" + title + "\n学历：" + degree + "\n请给出 2 条推荐提纲标题。"
        ));
        return parseOutlineList(text, title, degree);
    }

    public String generateOutlineText(String title, int depth) {
        if (aiConfigService.isMock()) {
            return mockOutlineText(title);
        }
        return aiConfigService.withLlmLimit("outline_generate", () -> llmClient.chat(
                "outline_generate",
                "你是论文提纲生成助手。按章节输出多级提纲，每行一个标题，使用「第一章」「1.1」等格式。",
                "论文题目：" + title + "\n提纲深度：" + depth + " 级\n请生成完整提纲。"
        ));
    }

    public String parseProposal(String text) {
        String raw = text == null ? "" : text.trim();
        if (raw.isBlank()) {
            return "未能提取到有效文本，请检查文件格式或改用 .docx / .txt。";
        }
        if (aiConfigService.isMock()) {
            int len = Math.min(raw.length(), 8000);
            String excerpt = raw.substring(0, len);
            if (excerpt.length() > 200) {
                excerpt = excerpt.substring(0, 200) + "…";
            }
            return "【已解析开题报告】研究背景、文献综述框架、研究方法已提取。原文节选：" + excerpt;
        }
        String input = raw.length() > 8000 ? raw.substring(0, 8000) : raw;
        return aiConfigService.withLlmLimit("parse_proposal", () -> llmClient.chat(
                "parse_proposal",
                "你是开题报告解析助手。提取研究背景、研究内容、研究方法、文献综述要点，简明扼要，不超过500字，不要 markdown。",
                input
        ));
    }

    public Map<String, Object> generatePaperPreview(String title, String outlineText) {
        return paperPreviewGenerator.generate(title, outlineText, aiConfigService.isMock());
    }

    public Map<String, Object> processTask(String taskType, Map<String, Object> payload) {
        return processTask(taskType, payload, null);
    }

    public Map<String, Object> processTask(String taskType, Map<String, Object> payload, IntConsumer progress) {
        String title = resolveTitle(payload);
        String outlineText = resolveOutlineText(payload);
        Map<String, Object> result = new HashMap<>();
        result.put("taskType", taskType);
        result.put("model", aiConfigService.modelForTaskType(taskType));
        result.put("provider", aiConfigService.getConfig().get("provider"));
        result.put("mock", aiConfigService.isMock());

        switch (taskType) {
            case "paper_generate" -> putPaperPreviewResult(result, title, outlineText, progress, payload);
            case "revise" -> {
                String content = String.valueOf(payload.getOrDefault("content", title));
                String out = aiConfigService.isMock()
                        ? "【改稿完成】已对全文进行学术化润色，优化逻辑衔接与术语规范性。\n\n" + content
                        : aiConfigService.withLlmLimit(taskType, () -> llmClient.chat(taskType,
                        "你是论文改稿助手。输出完整改稿后的正文，保持原意，提升学术表达与逻辑连贯性。",
                        "请对以下文稿进行学术化改稿：\n" + content));
                result.put("revisedText", out);
                result.put("message", "改稿完成");
            }
            case "paraphrase" -> {
                String content = String.valueOf(payload.getOrDefault("content", title));
                String content2 = payload.get("content2") != null ? String.valueOf(payload.get("content2")) : "";
                String prompt = content2.isBlank()
                        ? content
                        : "原文：\n" + content + "\n\n对照参考：\n" + content2 + "\n\n请对原文降重改写。";
                String out = aiConfigService.isMock()
                        ? richText("降重", title)
                        : aiConfigService.withLlmLimit(taskType, () -> llmClient.chat(taskType,
                        "你是降重助手，输出改写后的正文。",
                        prompt));
                result.put("paraphrasedText", out);
                result.put("message", "降重完成");
            }
            case "aigc_check" -> {
                String version = payload.get("checkVersion") != null
                        ? String.valueOf(payload.get("checkVersion")) : "standard";
                String versionHint = "strict".equals(version) ? "严格版" : "标准版";
                result.put("report", aiConfigService.isMock()
                        ? "AIGC 检测约 18%（" + versionHint + "），文本原创性良好，建议对摘要与结论段再做人工复核。"
                        : aiConfigService.withLlmLimit(taskType, () -> llmClient.chat(taskType,
                        "你是 AIGC 检测分析助手，输出简短检测报告，含预估 AIGC 比例与建议。",
                        versionHint + "检测：\n" + payload.getOrDefault("content", title))));
                result.put("message", "检测完成");
            }
            case "ppt_generate" -> {
                int pages = payload.get("pages") instanceof Number n ? n.intValue() : 15;
                String out = aiConfigService.isMock()
                        ? "第1页 封面：" + title + "\n第2页 研究背景\n第3页 研究方法\n第4页 结论"
                        : aiConfigService.withLlmLimit(taskType, () -> llmClient.chat(taskType,
                        "你是 PPT 大纲助手，输出每页标题与要点，用换行分页。",
                        "主题：" + title + "\n页数约：" + pages + " 页"));
                result.put("slidesOutline", out);
                result.put("slides", out.split("\n").length);
                result.put("message", "PPT 大纲已生成");
            }
            case "file_translate" -> {
                String content = String.valueOf(payload.getOrDefault("content", title));
                String out = aiConfigService.isMock()
                        ? "【译文】This study discusses the submitted content and related quality assurance mechanisms."
                        : aiConfigService.withLlmLimit(taskType, () -> llmClient.chat(taskType,
                        "你是学术翻译助手，输出完整、流畅的译文，保留专业术语准确性。",
                        "请将以下内容翻译为英文：\n" + content));
                result.put("translatedText", out);
                result.put("translated", true);
                result.put("message", "翻译完成");
            }
            case "data_analysis" -> {
                String req = payload.get("requirements") != null
                        ? String.valueOf(payload.get("requirements"))
                        : String.valueOf(payload.getOrDefault("content", title));
                result.put("reportSummary", aiConfigService.isMock()
                        ? "数据分析表明，样本在主要指标上存在显著差异，建议结合分组变量进一步检验。"
                        : aiConfigService.withLlmLimit(taskType, () -> llmClient.chat(taskType,
                        "你是数据分析助手，输出结构化分析摘要：数据概况、主要发现、图表建议、结论。",
                        req)));
                result.put("message", "分析完成");
            }
            default -> {
                putPaperPreviewResult(result, title, outlineText, progress, payload);
                result.put("message", "任务已完成");
            }
        }
        return result;
    }

    private void putPaperPreviewResult(
            Map<String, Object> result,
            String title,
            String outlineText,
            IntConsumer progress,
            Map<String, Object> payload
    ) {
        result.put("preview", paperPreviewGenerator.generate(
                title,
                outlineText,
                aiConfigService.isMock(),
                progress,
                payload
        ));
        result.put("message", "论文预览已生成");
    }

    public Map<String, Object> testConnection() {
        long start = System.currentTimeMillis();
        if (aiConfigService.isMock()) {
            Map<String, Object> m = new HashMap<>();
            m.put("ok", true);
            m.put("mode", "mock");
            m.put("latencyMs", System.currentTimeMillis() - start);
            m.put("message", "Mock 模式：将生成完整章节正文（无需外网）");
            return m;
        }
        String model = String.valueOf(aiConfigService.getConfig().get("modelName"));
        AtomicReference<LlmEndpoint> usedEndpoint = new AtomicReference<>();
        String reply = aiConfigService.withLlmLimit("connectivity_test", () -> {
            usedEndpoint.set(aiConfigService.requireEndpoint());
            return llmClient.chatWithModel(
                    model,
                    "你是连通性测试助手。",
                    "请只回复：OK"
            );
        });
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        m.put("mode", "llm");
        var ep = usedEndpoint.get();
        if (ep != null) {
            m.put("provider", ep.provider());
            m.put("model", aiConfigService.modelForTaskType("connectivity_test"));
            m.put("modelEndpoint", ep.label());
        } else {
            m.put("provider", aiConfigService.getConfig().get("provider"));
            m.put("model", model);
        }
        m.put("latencyMs", System.currentTimeMillis() - start);
        m.put("reply", reply);
        return m;
    }

    /** 依次测试线程池中所有模型并更新健康状态 */
    public Map<String, Object> testAllModels() {
        if (!aiConfigService.isMock() && aiConfigService.enabledEndpoints().isEmpty()) {
            throw new com.xiaowei.common.BusinessException("请先在模型线程池中添加并启用至少一个模型");
        }
        return aiModelHealthService.probeAll();
    }

    /** 自动探测全部模型可用性（与 test-all 相同，供页面加载时调用） */
    public Map<String, Object> probeAllModels() {
        return aiModelHealthService.probeAll();
    }

    private String richText(String kind, String title) {
        return "围绕「" + title + "」的" + kind + "处理已完成，已对论证结构、术语规范与段落衔接进行优化，"
                + "并保留核心观点与数据结论，可直接用于后续修改与排版。";
    }

    private List<String> mockRecommendTitles(String keyword) {
        List<String> list = new ArrayList<>();
        list.add(trimTitle(keyword + "系统中基于深度学习的智能分析与优化研究"));
        list.add(trimTitle(keyword + "环境下的关键技术与应用前景探讨"));
        list.add(trimTitle(keyword + "领域创新发展的影响因素及对策研究"));
        list.add(trimTitle(keyword + "背景下可持续发展路径与政策研究"));
        return list;
    }

    private String trimTitle(String title) {
        return title.length() > 50 ? title.substring(0, 50) : title;
    }

    private List<String> parseTitleList(String text, String keyword) {
        try {
            String json = extractJsonArray(text);
            List<String> list = objectMapper.readValue(json, new TypeReference<>() {});
            if (list == null || list.isEmpty()) {
                return mockRecommendTitles(keyword);
            }
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(this::trimTitle)
                    .limit(4)
                    .toList();
        } catch (Exception e) {
            return mockRecommendTitles(keyword);
        }
    }

    private List<Map<String, Object>> mockOutlineList(String title, String degree) {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> item = new HashMap<>();
        item.put("id", 1L);
        item.put("title", title + " - 标准结构提纲");
        item.put("degree", degree);
        item.put("depth", 2);
        list.add(item);
        return list;
    }

    private String mockOutlineText(String title) {
        return """
                第一章 绪论
                1.1 研究背景
                1.2 研究意义
                1.3 研究方法
                第二章 理论基础
                2.1 核心概念
                2.2 理论框架
                第三章 分析与讨论
                3.1 现状分析
                3.2 对策建议
                第四章 结论
                """.trim();
    }

    private List<Map<String, Object>> parseOutlineList(String text, String title, String degree) {
        try {
            String json = extractJsonArray(text);
            List<Map<String, Object>> list = objectMapper.readValue(json, new TypeReference<>() {});
            long id = 1;
            for (Map<String, Object> item : list) {
                item.putIfAbsent("id", id++);
                item.putIfAbsent("degree", degree);
                item.putIfAbsent("depth", 2);
            }
            return list;
        } catch (Exception e) {
            return mockOutlineList(title, degree);
        }
    }

    private String resolveTitle(Map<String, Object> payload) {
        String title = String.valueOf(payload.getOrDefault("title", "")).trim();
        return title.isBlank() || "null".equals(title) ? "论文" : title;
    }

    private String resolveOutlineText(Map<String, Object> payload) {
        String outline = String.valueOf(payload.getOrDefault("outlineText", "")).trim();
        return "null".equals(outline) ? "" : outline;
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
