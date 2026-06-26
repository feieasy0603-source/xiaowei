package com.xiaowei.integration.ai;

/** LLM 单次对话结果，用于判断是否因 max_tokens 截断及统计 Token */
public record LlmChatResult(
        String content,
        String finishReason,
        long promptTokens,
        long completionTokens,
        long totalTokens
) {

    public LlmChatResult(String content, String finishReason) {
        this(content, finishReason, 0, 0, 0);
    }

    public boolean truncated() {
        return "length".equalsIgnoreCase(finishReason);
    }
}
