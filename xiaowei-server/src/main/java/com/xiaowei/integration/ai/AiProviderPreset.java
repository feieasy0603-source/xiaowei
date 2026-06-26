package com.xiaowei.integration.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 常见 AI 运营商 / 模型网关预设（多为 OpenAI 兼容 Chat Completions）。
 */
public final class AiProviderPreset {

    public static final String MOCK = "mock";
    public static final String OPENAI = "openai";
    public static final String AZURE = "azure";
    public static final String DEEPSEEK = "deepseek";
    public static final String QWEN = "qwen";
    public static final String MOONSHOT = "moonshot";
    public static final String ZHIPU = "zhipu";
    public static final String CUSTOM = "custom";

    private AiProviderPreset() {
    }

    public record Preset(
            String id,
            String label,
            String defaultBaseUrl,
            String defaultModel,
            String authStyle,
            String hint
    ) {
    }

    private static final List<Preset> PRESETS = List.of(
            new Preset(MOCK, "Mock 演示", "", "mock", "none", "本地模拟，不调用外部 API"),
            new Preset(OPENAI, "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "bearer",
                    "官方 API，需海外或代理可达"),
            new Preset(DEEPSEEK, "DeepSeek", "https://api.deepseek.com", "deepseek-v4-flash", "bearer",
                    "OpenAI 兼容；模型：deepseek-v4-flash（快）/ deepseek-v4-pro（强）；"
                            + "deepseek-chat、deepseek-reasoner 为旧名，将弃用"),
            new Preset(QWEN, "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", "bearer",
                    "阿里云 DashScope 兼容模式"),
            new Preset(MOONSHOT, "Moonshot", "https://api.moonshot.cn/v1", "moonshot-v1-8k", "bearer",
                    "月之暗面 Kimi API"),
            new Preset(ZHIPU, "智谱 AI", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", "bearer",
                    "智谱 OpenAI 兼容 v4 路径"),
            new Preset(AZURE, "Azure OpenAI", "https://YOUR-RESOURCE.openai.azure.com", "YOUR-DEPLOYMENT", "api-key",
                    "部署名填在「默认模型」；需配置 api-version"),
            new Preset(CUSTOM, "自定义网关", "https://your-gateway.example.com/v1", "your-model", "bearer",
                    "任意 OpenAI 兼容 Chat Completions 地址")
    );

    public static List<Preset> all() {
        return PRESETS;
    }

    public static Preset find(String id) {
        if (id == null || id.isBlank()) {
            return PRESETS.get(0);
        }
        return PRESETS.stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElse(PRESETS.get(PRESETS.size() - 1));
    }

    public static List<Map<String, Object>> toAdminList() {
        return PRESETS.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id());
            m.put("label", p.label());
            m.put("defaultBaseUrl", p.defaultBaseUrl());
            m.put("defaultModel", p.defaultModel());
            m.put("authStyle", p.authStyle());
            m.put("hint", p.hint());
            return m;
        }).toList();
    }
}
