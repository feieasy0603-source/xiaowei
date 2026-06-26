package com.xiaowei.integration.ai;

import java.util.Map;

/** 按用户目标字数与章节数计算每节篇幅与 LLM token 上限 */
public final class PaperLengthPlanner {

    private final int targetTotalWords;
    private final int sectionCount;
    private final int wordsPerSection;
    private final int minSectionChars;
    private final int batchSize;
    private final int maxTokensFull;
    private final int maxTokensBatch;
    private final int maxTokensSection;

    private PaperLengthPlanner(
            int targetTotalWords,
            int sectionCount,
            int wordsPerSection,
            int minSectionChars,
            int batchSize,
            int maxTokensFull,
            int maxTokensBatch,
            int maxTokensSection
    ) {
        this.targetTotalWords = targetTotalWords;
        this.sectionCount = sectionCount;
        this.wordsPerSection = wordsPerSection;
        this.minSectionChars = minSectionChars;
        this.batchSize = batchSize;
        this.maxTokensFull = maxTokensFull;
        this.maxTokensBatch = maxTokensBatch;
        this.maxTokensSection = maxTokensSection;
    }

    public static PaperLengthPlanner from(Map<String, Object> context, int outlineSectionCount) {
        int target = parseWordCount(context);
        int sections = Math.max(4, outlineSectionCount);
        int per = target / sections;
        // 节数多时降低单节下限，避免总字数膨胀到无法写完
        int perFloor = sections > 32 ? 160 : sections > 24 ? 200 : sections > 16 ? 280 : 400;
        per = Math.max(perFloor, Math.min(per, 1200));
        double minRatio = sections > 28 ? 0.55 : sections > 18 ? 0.65 : 0.8;
        int minChars = (int) (per * minRatio);
        // 短提纲可并行 2–3 节；长提纲保持逐节避免截断
        int batch = sections <= 12 ? 3 : sections <= 24 ? 2 : 1;
        int tokensPerSection = Math.min(6144, Math.max(1536, (int) (per * 2.2)));
        int tokensFull = Math.min(8192, tokensPerSection * Math.min(sections, 4));
        return new PaperLengthPlanner(target, sections, per, minChars, batch, tokensFull, tokensPerSection, tokensPerSection);
    }

    public static int parseTargetWordCount(Map<String, Object> context) {
        return parseWordCount(context);
    }

    private static int parseWordCount(Map<String, Object> context) {
        int target = 8000;
        if (context == null) return target;
        Object wc = context.get("wordCount");
        if (wc instanceof Number n) {
            target = n.intValue();
        } else if (wc != null) {
            try {
                target = Integer.parseInt(String.valueOf(wc).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(4000, Math.min(target, 50000));
    }

    public int targetTotalWords() {
        return targetTotalWords;
    }

    public int wordsPerSection() {
        return wordsPerSection;
    }

    public int minSectionChars() {
        return minSectionChars;
    }

    public int batchSize() {
        return batchSize;
    }

    public int maxTokensFull() {
        return maxTokensFull;
    }

    public int maxTokensBatch() {
        return maxTokensBatch;
    }

    public int maxTokensSection() {
        return maxTokensSection;
    }

    public String sectionLengthHint() {
        return wordsPerSection + "-" + (wordsPerSection + 200) + " 字";
    }
}
