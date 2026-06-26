package com.xiaowei.integration.ai;

import com.xiaowei.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

/**
 * 生成可用的论文预览（摘要 + 分章节正文），按用户 meta.wordCount 分配每节篇幅。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaperPreviewGenerator {

    /** 单篇最多生成节数（超过则截断并打日志；不再按总字数砍掉后半提纲） */
    private static final int HARD_MAX_SECTIONS = 80;
    private static final Pattern CHAPTER_LINE = Pattern.compile("^第[一二三四五六七八九十0-9]+章\\s+.+");
    private static final Pattern NUMBERED_SECTION = Pattern.compile("^\\d+\\.\\d+(\\.\\d+)?\\s+.+");

    private final AiConfigService aiConfigService;
    private final OpenAiCompatibleClient llmClient;

    public Map<String, Object> generate(String title, String outlineText, boolean mock) {
        return generate(title, outlineText, mock, null, Map.of());
    }

    public Map<String, Object> generate(
            String title,
            String outlineText,
            boolean mock,
            IntConsumer progress,
            Map<String, Object> context
    ) {
        Map<String, Object> ctx = context != null ? context : Map.of();
        String outline = normalizeOutline(outlineText, title);
        String literature = contextText(ctx);
        String researchNotes = notesContext(ctx);
        List<String> outlineLines = splitOutlineLines(outline);
        List<String> sectionTitles = pickSectionTitles(outlineLines);
        PaperLengthPlanner plan = PaperLengthPlanner.from(ctx, sectionTitles.size());
        int outlineSectionCount = countOutlineSections(outlineLines);
        if (sectionTitles.size() < outlineSectionCount) {
            log.warn("提纲共 {} 节，受上限限制将生成前 {} 节（请精简提纲或拆分为多次任务）",
                    outlineSectionCount, sectionTitles.size());
        } else {
            log.info("将按提纲生成全部 {} 节", sectionTitles.size());
        }
        @SuppressWarnings("unchecked")
        Consumer<Map<String, Object>> sectionCheckpoint = ctx.get("_sectionCheckpoint") instanceof Consumer<?> c
                ? (Consumer<Map<String, Object>>) c
                : null;

        tick(progress, 12);
        if (mock) {
            Map<String, Object> preview = buildOutlineSectionPreview(
                    title, sectionTitles, true, literature, researchNotes, plan, progress, sectionCheckpoint);
            tick(progress, 95);
            return preview;
        }

        // 逐节生成：整篇 JSON 极易在 max_tokens 处截断，导致后几节空白或解析失败
        tick(progress, 18);
        return buildOutlineSectionPreview(
                title, sectionTitles, false, literature, researchNotes, plan, progress, sectionCheckpoint);
    }

    private Map<String, Object> buildOutlineSectionPreview(
            String title,
            List<String> sectionTitles,
            boolean mock,
            String literature,
            String researchNotes,
            PaperLengthPlanner plan,
            IntConsumer progress,
            Consumer<Map<String, Object>> sectionCheckpoint
    ) {
        List<Map<String, String>> sections = new ArrayList<>();
        int total = sectionTitles.size();
        int done = 0;
        int batchSize = plan.batchSize();

        for (int i = 0; i < sectionTitles.size(); i += batchSize) {
            List<String> batch = sectionTitles.subList(i, Math.min(i + batchSize, sectionTitles.size()));
            if (mock) {
                for (String line : batch) {
                    sections.add(sectionEntry(line,
                            richSectionBody(title, line, sections, literature, plan.wordsPerSection())));
                }
            } else if (batch.size() > 1) {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> batchResults =
                        new ArrayList<>(java.util.Collections.nCopies(batch.size(), null));
                java.util.stream.IntStream.range(0, batch.size()).parallel().forEach(j -> {
                    String line = batch.get(j);
                    int sectionIndex = sections.size() + j;
                    try {
                        batchResults.set(j, sectionEntry(line,
                                generateSingleSection(
                                        title, line, sectionIndex, sectionTitles, sections,
                                        literature, researchNotes, plan)));
                    } catch (Exception e) {
                        log.warn("章节生成失败 {}: {}", line, e.getMessage());
                        batchResults.set(j, sectionEntry(line,
                                richSectionBody(title, line, sections, literature, plan.wordsPerSection())));
                    }
                });
                batchResults.stream().filter(java.util.Objects::nonNull).forEach(sections::add);
            } else {
                for (String line : batch) {
                    int sectionIndex = sections.size();
                    try {
                        sections.add(sectionEntry(line,
                                generateSingleSection(
                                        title, line, sectionIndex, sectionTitles, sections,
                                        literature, researchNotes, plan)));
                    } catch (Exception e) {
                        log.warn("章节生成失败 {}: {}", line, e.getMessage());
                        sections.add(sectionEntry(line,
                                richSectionBody(title, line, sections, literature, plan.wordsPerSection())));
                    }
                }
            }
            done += batch.size();
            if (done % 3 == 0 || done >= total) {
                fireSectionCheckpoint(sectionCheckpoint, sections, done, total);
            }
            if (progress != null && total > 0) {
                int pct = 28 + (int) ((done * 58.0) / total);
                progress.accept(Math.min(88, pct));
            }
        }

        List<Map<String, String>> alignedSections = alignSectionsToOutline(
                title, sectionTitles, sections, mock, literature, researchNotes, plan, sectionCheckpoint);

        fireSectionCheckpoint(sectionCheckpoint, alignedSections, total, total);

        tick(progress, 90);
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("abstractZh", buildAbstractZh(title, alignedSections, mock, literature, plan));
        preview.put("abstractEn", buildAbstractEn(title, alignedSections, mock));
        preview.put("sections", alignedSections);
        preview.put("targetWordCount", plan.targetTotalWords());
        preview.put("plannedSections", total);
        preview.put("generatedSections", alignedSections.size());
        preview.put("approxWords", estimateWords(preview));
        return preview;
    }

    private void fireSectionCheckpoint(
            Consumer<Map<String, Object>> checkpoint,
            List<Map<String, String>> sections,
            int done,
            int total
    ) {
        if (checkpoint == null) {
            return;
        }
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("sections", new ArrayList<>(sections));
        snap.put("done", done);
        snap.put("total", total);
        try {
            checkpoint.accept(snap);
        } catch (Exception e) {
            log.warn("分段保存回调失败: {}", e.getMessage());
        }
    }

    /** 按提纲顺序补齐缺失或过短的章节，避免只生成前几节 */
    private List<Map<String, String>> alignSectionsToOutline(
            String title,
            List<String> sectionTitles,
            List<Map<String, String>> generated,
            boolean mock,
            String literature,
            String researchNotes,
            PaperLengthPlanner plan,
            Consumer<Map<String, Object>> sectionCheckpoint
    ) {
        int minLen = plan.minSectionChars() / 2;
        if (!mock && generated.size() == sectionTitles.size()) {
            boolean allOk = true;
            for (int i = 0; i < sectionTitles.size(); i++) {
                String wanted = sectionTitles.get(i);
                Map<String, String> sec = generated.get(i);
                if (sec == null || !lengthOk(sec.get("content"), minLen)) {
                    allOk = false;
                    break;
                }
                if (!titleMatches(wanted, sec.get("title"))) {
                    allOk = false;
                    break;
                }
            }
            if (allOk) {
                return generated;
            }
        }
        Map<String, String> contentByTitle = new LinkedHashMap<>();
        for (Map<String, String> sec : generated) {
            if (sec.get("title") != null && sec.get("content") != null) {
                contentByTitle.put(sec.get("title"), sec.get("content"));
            }
        }
        List<Map<String, String>> ordered = new ArrayList<>();
        for (int i = 0; i < sectionTitles.size(); i++) {
            String wanted = sectionTitles.get(i);
            String content = contentByTitle.get(wanted);
            if (content == null) {
                content = contentByTitle.entrySet().stream()
                        .filter(e -> titleMatches(wanted, e.getKey()))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null);
            }
            if (!lengthOk(content, minLen)) {
                log.warn("章节缺失或过短，重新生成: {}", wanted);
                if (mock) {
                    content = richSectionBody(title, wanted, ordered, literature, plan.wordsPerSection());
                } else {
                    try {
                        content = generateSingleSection(
                                title, wanted, i, sectionTitles, ordered, literature, researchNotes, plan);
                    } catch (Exception e) {
                        content = richSectionBody(title, wanted, ordered, literature, plan.wordsPerSection());
                    }
                }
            }
            ordered.add(sectionEntry(wanted, padToMinLength(content, plan.minSectionChars())));
            fireSectionCheckpoint(sectionCheckpoint, ordered, ordered.size(), sectionTitles.size());
        }
        return ordered;
    }

    private String generateSingleSection(
            String title,
            String section,
            int sectionIndex,
            List<String> allSectionTitles,
            List<Map<String, String>> priorSections,
            String literature,
            String researchNotes,
            PaperLengthPlanner plan
    ) {
        String system = """
                你是中文学术论文写作助手。全文必须主题一致、术语统一、论证连贯。
                只输出「当前节」正文：不要节标题、不要 markdown、不要 JSON、不要编号列表。
                必须写满 %s，可分 3-5 段，段末完整收束。
                若上文已有内容：首段必须自然承接，禁止重复上文已写观点，禁止另起炉灶。
                专有名词（如 Hadoop、MapReduce、Hive）全文保持一致。
                """.formatted(plan.sectionLengthHint()).trim();
        String user = singleSectionPrompt(
                title, section, sectionIndex, allSectionTitles, priorSections, literature, researchNotes, plan);

        LlmChatResult first = aiConfigService.withLlmLimit("paper_generate", () ->
                llmClient.chatCompletionDetailed("paper_generate", system, user, false, plan.maxTokensSection()));

        StringBuilder body = new StringBuilder(first.content().trim());
        boolean truncated = first.truncated();
        for (int round = 0; truncated && body.length() > 80 && round < 3; round++) {
            log.info("章节输出被截断，续写({}/3): {}", round + 1, section);
            String contUser = "上文因长度限制被截断，请从断点接着写完本节，不要重复已写内容，不要标题。\n\n已写内容：\n"
                    + tailForContinue(body.toString(), 1500);
            LlmChatResult next = aiConfigService.withLlmLimit("paper_generate", () ->
                    llmClient.chatCompletionDetailed(
                            "paper_generate",
                            "你是学术论文续写助手。只输出续写正文，使本节达到完整收束。",
                            contUser,
                            false,
                            plan.maxTokensSection()));
            if (next.content().isBlank()) {
                break;
            }
            body.append("\n\n").append(next.content().trim());
            truncated = next.truncated();
        }
        return padToMinLength(body.toString(), plan.minSectionChars());
    }

    private String tailForContinue(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(text.length() - maxChars);
    }

    private String singleSectionPrompt(
            String title,
            String section,
            int sectionIndex,
            List<String> allSectionTitles,
            List<Map<String, String>> priorSections,
            String literature,
            String researchNotes,
            PaperLengthPlanner plan
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("【论文题目】").append(title).append("\n\n");
        sb.append("【全文提纲（保持整体逻辑，当前节已标注）】\n");
        sb.append(buildOutlineBlock(allSectionTitles, sectionIndex)).append("\n");
        sb.append("【上文衔接（务必承接，勿重复）】\n");
        sb.append(buildPriorContextBlock(priorSections)).append("\n\n");
        sb.append("【当前节】").append(section).append("\n");
        sb.append("本节目标字数：约 ").append(plan.wordsPerSection()).append(" 字。\n");
        if (isChapterLine(section)) {
            sb.append("提示：本节为章标题层级，请写本章导语段，概括本章结构并承接上一章。\n");
        } else if (sectionIndex > 0 && isChapterLine(allSectionTitles.get(sectionIndex - 1))) {
            sb.append("提示：本节为章下首节，首句须承接上一章结论并引入本节主题。\n");
        }
        if (sectionIndex < allSectionTitles.size() - 1) {
            sb.append("下一节将写：").append(allSectionTitles.get(sectionIndex + 1))
                    .append("，段末可适度铺垫。\n");
        }
        if (!literature.isBlank()) {
            sb.append("\n【参考文献（可引用）】\n").append(literature).append("\n");
        }
        if (!researchNotes.isBlank()) {
            sb.append("\n【研究备注】").append(researchNotes).append("\n");
        }
        sb.append("\n请撰写本节完整正文。");
        return sb.toString();
    }

    private String buildOutlineBlock(List<String> titles, int currentIndex) {
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, currentIndex - 3);
        int to = Math.min(titles.size() - 1, currentIndex + 3);
        if (from > 0) {
            sb.append("…（前略 ").append(from).append(" 节）\n");
        }
        for (int i = from; i <= to; i++) {
            sb.append(i + 1).append(". ").append(titles.get(i));
            if (i == currentIndex) {
                sb.append("  ← 当前撰写");
            }
            sb.append("\n");
        }
        if (to < titles.size() - 1) {
            sb.append("…（后略 ").append(titles.size() - 1 - to).append(" 节）");
        }
        return sb.toString();
    }

    /** 最近 2 节末尾 + 更早 1 节一句话摘要，控制 token */
    private String buildPriorContextBlock(List<Map<String, String>> priorSections) {
        if (priorSections == null || priorSections.isEmpty()) {
            return "（本节之前尚无正文，请作为全文开篇相关章节，交代背景并点明后文结构。）";
        }
        StringBuilder sb = new StringBuilder();
        int n = priorSections.size();
        if (n > 2) {
            Map<String, String> early = priorSections.get(n - 3);
            String t = early.get("title");
            String c = early.get("content");
            sb.append("更早章节「").append(t).append("」要点：")
                    .append(summarizeBrief(c)).append("\n\n");
        }
        int tailStart = Math.max(0, n - 2);
        for (int i = tailStart; i < n; i++) {
            Map<String, String> sec = priorSections.get(i);
            String t = sec.get("title");
            String c = sec.get("content");
            sb.append("【").append(t).append("】结尾片段：\n");
            if (c != null && !c.isBlank()) {
                sb.append(tailForContinue(c, 450)).append("\n\n");
            }
        }
        sb.append("写作约束：首段必须承接上述结尾；术语、研究对象与上文一致。");
        return sb.toString();
    }

    private String summarizeBrief(String content) {
        if (content == null || content.isBlank()) {
            return "（略）";
        }
        String t = content.trim().replaceAll("\\s+", " ");
        if (t.length() <= 120) {
            return t;
        }
        return t.substring(0, 120) + "…";
    }

    private boolean isChapterLine(String line) {
        return line != null && CHAPTER_LINE.matcher(line.trim()).matches();
    }

    private Map<String, String> sectionEntry(String title, String content) {
        Map<String, String> sec = new LinkedHashMap<>();
        sec.put("title", title);
        sec.put("content", content);
        return sec;
    }

    private boolean titleMatches(String wanted, String got) {
        if (got == null) return false;
        return wanted.equals(got) || wanted.contains(got) || got.contains(wanted);
    }

    private boolean lengthOk(String content, int min) {
        return content != null && content.length() >= min;
    }

    /** 不足目标字数时用结构化段落补足（Mock / LLM 偏短时） */
    private String padToMinLength(String content, int minChars) {
        if (content == null) content = "";
        if (content.length() >= minChars) {
            return content;
        }
        String[] pads = {
                "进一步从理论与实践两个层面展开分析，可以发现研究对象在不同情境下呈现出显著差异，"
                        + "这既与宏观政策环境有关，也与微观主体行为选择密切相关。",
                "在数据与案例支撑方面，相关研究多采用问卷调查、深度访谈与二手资料相结合的方法，"
                        + "从而提升结论的可信度与可推广性。",
                "从政策含义看，应在制度设计、过程管理与技术赋能之间建立协同机制，"
                        + "形成可评价、可追溯、可迭代的闭环管理体系。",
                "对高校教学与科研实践而言，应强化选题论证、过程指导与成果验收，"
                        + "引导学生形成规范学术写作与严谨论证能力。",
                "综上所述，本节论证为全文后续章节提供概念基础与分析框架，"
                        + "并为最终对策建议奠定依据。",
        };
        StringBuilder sb = new StringBuilder(content.trim());
        int i = 0;
        while (sb.length() < minChars && i < 30) {
            if (!sb.isEmpty() && !sb.toString().endsWith("\n")) {
                sb.append("\n\n");
            }
            sb.append(pads[i % pads.length]);
            i++;
        }
        return sb.toString();
    }

    private String richSectionBody(
            String title,
            String sectionTitle,
            List<Map<String, String>> priorSections,
            String literature,
            int targetWords
    ) {
        String litHint = literature.isBlank()
                ? "结合该领域常见研究与政策文件"
                : "结合用户选定的参考文献与行业实践";
        String bridge = "";
        if (priorSections != null && !priorSections.isEmpty()) {
            Map<String, String> last = priorSections.get(priorSections.size() - 1);
            bridge = "承接上一节「" + last.get("title") + "」的结论，";
        }
        String[] blocks = {
                bridge + "围绕「" + title + "」中的「" + sectionTitle + "」一节，本文从研究背景出发，梳理相关理论基础与政策环境，"
                        + litHint + "，说明关键影响因素与作用机制。",
                "在分析过程中，采用规范的研究思路，对核心概念进行界定，并通过案例与逻辑推演说明变量之间的内在联系，"
                        + "同时讨论不同研究路径的适用边界。",
                "从实证与规范分析相结合的角度看，相关结论表明问题成因具有多维性，"
                        + "需要从制度、技术与行为协同视角综合理解，避免单一因素解释带来的偏差。",
                "在实践层面，改进路径应坚持问题导向与可操作性，强调过程监控与结果评价并重，"
                        + "并通过分阶段目标管理提升实施效果。",
                "对高校毕业论文质量保障而言，应建立覆盖选题、开题、中期检查、查重与答辩的闭环机制，"
                        + "使学生在完整科研训练中形成严谨写作习惯。",
                "本节进一步归纳：第一，理论框架需与研究问题高度匹配；第二，文献综述应突出评述而非堆砌；"
                        + "第三，对策建议需对应前文分析结论并保持可验证性。",
                "综上，本节内容为后文对策建议提供依据，并为全文论证链条提供支撑，"
                        + "有助于读者把握研究逻辑与章节之间的衔接关系。",
        };
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (sb.length() < targetWords && i < 40) {
            if (!sb.isEmpty()) sb.append("\n\n");
            sb.append(blocks[i % blocks.length]);
            i++;
        }
        return sb.toString();
    }

    /**
     * 按提纲原文顺序：保留「第X章」与各級编号小节（1.1、3.1.3 等），不再只取其中一类或截断为 14 节。
     */
    private List<String> pickSectionTitles(List<String> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }
        boolean hasNumbered = lines.stream().anyMatch(l -> NUMBERED_SECTION.matcher(l).matches());
        List<String> ordered = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (NUMBERED_SECTION.matcher(t).matches() || CHAPTER_LINE.matcher(t).matches()) {
                ordered.add(t);
            } else if (!hasNumbered && t.length() >= 2) {
                ordered.add(t);
            }
        }
        if (ordered.isEmpty()) {
            ordered.addAll(lines.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
        }
        int limit = sectionBudget(ordered.size());
        if (ordered.size() <= limit) {
            return ordered;
        }
        return new ArrayList<>(ordered.subList(0, limit));
    }

    /** 提纲节数优先全部生成，避免因总字数预算只写前 25～40 节导致第五章之后缺失 */
    private static int sectionBudget(int outlineSize) {
        if (outlineSize <= HARD_MAX_SECTIONS) {
            return outlineSize;
        }
        log.warn("提纲 {} 节超过硬上限 {}，将截断", outlineSize, HARD_MAX_SECTIONS);
        return HARD_MAX_SECTIONS;
    }

    private int countOutlineSections(List<String> lines) {
        boolean hasNumbered = lines.stream().anyMatch(l -> NUMBERED_SECTION.matcher(l).matches());
        int n = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (NUMBERED_SECTION.matcher(t).matches() || CHAPTER_LINE.matcher(t).matches()) {
                n++;
            } else if (!hasNumbered && t.length() >= 2) {
                n++;
            }
        }
        return n > 0 ? n : lines.size();
    }

    private int estimateWords(Map<String, Object> preview) {
        int n = 0;
        Object abs = preview.get("abstractZh");
        if (abs != null) n += String.valueOf(abs).length();
        Object sectionsObj = preview.get("sections");
        if (sectionsObj instanceof List<?> sections) {
            for (Object o : sections) {
                if (o instanceof Map<?, ?> sec && sec.get("content") != null) {
                    n += String.valueOf(sec.get("content")).length();
                }
            }
        }
        return n;
    }

    private String normalizeOutline(String outlineText, String title) {
        if (outlineText != null && !outlineText.isBlank()) {
            return outlineText.trim();
        }
        return defaultOutline(title);
    }

    private String defaultOutline(String title) {
        return """
                第一章 绪论
                1.1 研究背景与问题提出
                1.2 研究目的与意义
                1.3 研究内容与方法
                第二章 理论基础与文献综述
                2.1 核心概念界定
                2.2 国内外研究现状
                2.3 理论分析框架
                第三章 研究设计与实证分析
                3.1 研究对象与数据来源
                3.2 分析过程与结果
                3.3 结果讨论
                第四章 结论与展望
                4.1 研究结论
                4.2 实践建议
                4.3 研究不足与未来方向
                """.trim();
    }

    private List<String> splitOutlineLines(String outline) {
        List<String> lines = new ArrayList<>();
        for (String line : outline.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                lines.add(t);
            }
        }
        return lines;
    }

    private String buildAbstractZh(
            String title,
            List<Map<String, String>> sections,
            boolean mock,
            String literature,
            PaperLengthPlanner plan
    ) {
        if (mock) {
            String lit = literature.isBlank() ? "相关文献" : "选定文献";
            return padToMinLength(
                    """
                            本文以「%s」为研究主题，在梳理%s的基础上，明确研究问题与分析框架，
                            从理论阐释、现状诊断与路径设计三个层面展开论证。研究围绕%s等核心章节，系统分析关键变量与作用机制，
                            指出质量保障体系建设需要协同推进标准制定、过程管理与技术赋能，形成可评价、可追溯、可改进的闭环机制。
                            最后提出针对性对策建议，为相关领域实践与高校毕业论文质量管理提供参考。
                            """.formatted(
                            title,
                            lit,
                            sections.stream().limit(3).map(s -> s.get("title"))
                                    .reduce((a, b) -> a + "、" + b).orElse("各章")
                    ).replaceAll("\\s+", ""),
                    320
            );
        }
        try {
            String sectionBrief = sections.stream()
                    .limit(5)
                    .map(s -> s.get("title"))
                    .reduce((a, b) -> a + "；" + b)
                    .orElse("");
            String raw = aiConfigService.withLlmLimit("paper_generate", () -> llmClient.chatCompletion(
                    "paper_generate",
                    "你是学术摘要写作助手。只输出中文摘要正文，350-450字，不要标题。",
                    "论文题目：" + title + "\n主要章节：" + sectionBrief + "\n请撰写中文摘要。",
                    false,
                    1536
            ));
            if (raw != null && raw.length() >= 120 && !raw.contains("演示")) {
                return raw.trim();
            }
        } catch (Exception ignored) {
            /* 回退 */
        }
        StringBuilder sb = new StringBuilder();
        sb.append("本文围绕「").append(title).append("」展开研究，全文约 ")
                .append(plan.targetTotalWords()).append(" 字。");
        for (int i = 0; i < Math.min(4, sections.size()); i++) {
            sb.append(sections.get(i).get("title")).append("部分深入分析；");
        }
        sb.append("并提出对策建议。");
        return padToMinLength(sb.toString(), 300);
    }

    private String buildAbstractEn(String title, List<Map<String, String>> sections, boolean mock) {
        if (!mock && !sections.isEmpty()) {
            try {
                String raw = aiConfigService.withLlmLimit("paper_generate", () -> llmClient.chatCompletion(
                        "paper_generate",
                        "你是学术摘要写作助手。只输出英文摘要，180-250 words.",
                        "Paper title: " + title + "\nWrite an English abstract.",
                        false,
                        1536
                ));
                if (raw != null && raw.length() >= 80) {
                    return raw.trim();
                }
            } catch (Exception ignored) {
                /* fallback */
            }
        }
        return "This paper examines \"" + title + "\", reviews relevant literature, "
                + "presents an analytical framework across multiple sections, and proposes practical recommendations.";
    }

    private String contextText(Map<String, Object> context) {
        if (context == null) return "";
        Object lit = context.get("literatureContext");
        if (lit != null && !String.valueOf(lit).isBlank()) {
            return String.valueOf(lit).trim();
        }
        return "";
    }

    private String notesContext(Map<String, Object> context) {
        if (context == null) return "";
        Object notes = context.get("researchNotes");
        return notes != null ? String.valueOf(notes).trim() : "";
    }

    private void tick(IntConsumer progress, int value) {
        if (progress != null) {
            progress.accept(value);
        }
    }
}
