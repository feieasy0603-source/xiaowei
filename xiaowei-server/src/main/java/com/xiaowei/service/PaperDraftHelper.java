package com.xiaowei.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 从论文草稿 JSON 提取生成任务所需字段 */
public final class PaperDraftHelper {

    private static final String[] CN_NUMS = {"一", "二", "三", "四", "五", "六", "七", "八", "九", "十"};

    private PaperDraftHelper() {
    }

    /**
     * 优先使用专业版保存的 outlineText 原文，其次由 outline 节点拼装。
     */
    @SuppressWarnings("unchecked")
    public static String resolveOutlineText(Map<String, Object> draft) {
        Object rawText = draft.get("outlineText");
        if (rawText != null) {
            String text = String.valueOf(rawText).trim();
            if (!text.isBlank() && !"null".equals(text)) {
                return text;
            }
        }
        return outlineTextFromNodes(draft);
    }

    @SuppressWarnings("unchecked")
    public static String outlineTextFromNodes(Map<String, Object> draft) {
        Object raw = draft.get("outline");
        if (!(raw instanceof List<?> nodes) || nodes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int chapter = 0;
        int section = 0;
        int subsection = 0;
        for (Object nodeObj : nodes) {
            if (!(nodeObj instanceof Map<?, ?> node)) continue;
            int level = node.get("level") instanceof Number n ? n.intValue() : 1;
            String title = node.get("title") != null ? String.valueOf(node.get("title")).trim() : "";
            if (title.isEmpty()) continue;
            if (level == 1) {
                chapter++;
                section = 0;
                subsection = 0;
                String cn = chapter <= CN_NUMS.length ? CN_NUMS[chapter - 1] : String.valueOf(chapter);
                sb.append("第").append(cn).append("章 ").append(title).append('\n');
            } else if (level == 2) {
                section++;
                subsection = 0;
                sb.append(chapter).append('.').append(section).append(' ').append(title).append('\n');
            } else {
                subsection++;
                sb.append(chapter).append('.').append(section).append('.').append(subsection)
                        .append(' ').append(title).append('\n');
            }
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    public static String literatureContextFromDraft(Map<String, Object> draft, int maxItems) {
        Object raw = draft.get("literature");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            String title = m.get("title") != null ? String.valueOf(m.get("title")).trim() : "";
            if (title.isEmpty()) continue;
            String authors = m.get("authors") != null ? String.valueOf(m.get("authors")).trim() : "";
            if (authors.isEmpty() && m.get("author") != null) {
                authors = String.valueOf(m.get("author")).trim();
            }
            String year = m.get("year") != null ? String.valueOf(m.get("year")).trim() : "";
            String line = title;
            if (!authors.isEmpty() || !year.isEmpty()) {
                line = authors + (year.isEmpty() ? "" : " (" + year + ")") + ". " + title;
            }
            lines.add(line);
            if (lines.size() >= maxItems) break;
        }
        return lines.isEmpty() ? "" : lines.stream().collect(Collectors.joining("\n"));
    }

    @SuppressWarnings("unchecked")
    public static void mergeDraftIntoPayload(Map<String, Object> draft, Map<String, Object> payload) {
        if (draft.get("title") != null && !String.valueOf(draft.get("title")).isBlank()) {
            payload.putIfAbsent("title", String.valueOf(draft.get("title")).trim());
        }
        String outline = resolveOutlineText(draft);
        if (!outline.isBlank()) {
            payload.put("outlineText", outline);
        }
        String lit = literatureContextFromDraft(draft, 12);
        if (!lit.isBlank()) {
            payload.putIfAbsent("literatureContext", lit);
        }
        if (draft.get("meta") instanceof Map<?, ?> meta) {
            if (meta.get("degree") != null) payload.putIfAbsent("degree", meta.get("degree"));
            if (meta.get("wordCount") != null) payload.putIfAbsent("wordCount", meta.get("wordCount"));
        }
        if (draft.get("model") != null) {
            payload.putIfAbsent("modelType", draft.get("model"));
        }
        if (draft.get("researchNotes") != null && !String.valueOf(draft.get("researchNotes")).isBlank()) {
            payload.putIfAbsent("researchNotes", String.valueOf(draft.get("researchNotes")).trim());
        }
        if (draft.get("proposalParsed") != null && !String.valueOf(draft.get("proposalParsed")).isBlank()) {
            String proposal = String.valueOf(draft.get("proposalParsed")).trim();
            String notes = payload.get("researchNotes") != null
                    ? String.valueOf(payload.get("researchNotes")).trim()
                    : "";
            if (!notes.contains(proposal)) {
                String merged = notes.isBlank()
                        ? "【开题报告摘要】\n" + proposal
                        : notes + "\n\n【开题报告摘要】\n" + proposal;
                payload.put("researchNotes", merged);
            }
        }
    }
}
