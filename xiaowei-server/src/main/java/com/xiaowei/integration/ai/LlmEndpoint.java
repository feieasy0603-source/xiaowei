package com.xiaowei.integration.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 * 单个可调度的大模型接入点（独立 Base URL / Key / 并发）。
 */
public final class LlmEndpoint {

    private final String id;
    private final String label;
    private final boolean enabled;
    private final String provider;
    private final String baseUrl;
    private final String modelName;
    private final String apiKey;
    private final String organization;
    private final String apiVersion;
    private final int maxConcurrency;
    private final Semaphore semaphore;

    public LlmEndpoint(
            String id,
            String label,
            boolean enabled,
            String provider,
            String baseUrl,
            String modelName,
            String apiKey,
            String organization,
            String apiVersion,
            int maxConcurrency
    ) {
        this.id = id != null && !id.isBlank() ? id : UUID.randomUUID().toString();
        this.label = label != null && !label.isBlank() ? label : this.id;
        this.enabled = enabled;
        this.provider = provider != null ? provider : AiProviderPreset.OPENAI;
        this.baseUrl = baseUrl != null ? baseUrl : "";
        this.modelName = modelName != null ? modelName : "";
        this.apiKey = apiKey != null ? apiKey : "";
        this.organization = organization != null ? organization : "";
        this.apiVersion = apiVersion != null && !apiVersion.isBlank() ? apiVersion : "2024-02-15-preview";
        int mc = Math.max(1, maxConcurrency);
        this.maxConcurrency = mc;
        this.semaphore = new Semaphore(mc);
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public boolean enabled() {
        return enabled;
    }

    public String provider() {
        return provider;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String modelName() {
        return modelName;
    }

    public String apiKey() {
        return apiKey;
    }

    public String organization() {
        return organization;
    }

    public String apiVersion() {
        return apiVersion;
    }

    public int maxConcurrency() {
        return maxConcurrency;
    }

    public Semaphore semaphore() {
        return semaphore;
    }

    public int availablePermits() {
        return semaphore.availablePermits();
    }

    public Map<String, Object> toAdminMap(boolean maskApiKey) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("label", label);
        m.put("enabled", enabled);
        m.put("provider", provider);
        m.put("baseUrl", baseUrl);
        m.put("modelName", modelName);
        m.put("organization", organization);
        m.put("apiVersion", apiVersion);
        m.put("maxConcurrency", maxConcurrency);
        boolean keySet = apiKey != null && !apiKey.isBlank();
        m.put("apiKeySet", keySet);
        if (maskApiKey) {
            m.put("apiKey", maskKey(apiKey));
        } else {
            m.put("apiKey", apiKey);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    public static LlmEndpoint fromMap(Map<String, Object> raw, LlmEndpoint defaults) {
        String id = str(raw.get("id"));
        if (id.isBlank() && defaults != null) {
            id = defaults.id();
        }
        String label = str(raw.get("label"));
        if (label.isBlank() && defaults != null) {
            label = defaults.label();
        }
        boolean enabled = raw.get("enabled") == null
                ? (defaults == null || defaults.enabled())
                : Boolean.TRUE.equals(raw.get("enabled"));
        String provider = str(raw.get("provider"));
        if (provider.isBlank() && defaults != null) {
            provider = defaults.provider();
        }
        String baseUrl = str(raw.get("baseUrl"));
        if (baseUrl.isBlank() && defaults != null) {
            baseUrl = defaults.baseUrl();
        }
        String modelName = str(raw.get("modelName"));
        if (modelName.isBlank() && defaults != null) {
            modelName = defaults.modelName();
        }
        String apiKey = str(raw.get("apiKey"));
        if ((apiKey.isBlank() || apiKey.contains("****")) && defaults != null) {
            apiKey = defaults.apiKey();
        }
        String organization = str(raw.get("organization"));
        if (organization.isBlank() && defaults != null) {
            organization = defaults.organization();
        }
        String apiVersion = str(raw.get("apiVersion"));
        if (apiVersion.isBlank() && defaults != null) {
            apiVersion = defaults.apiVersion();
        }
        int maxConcurrency = intVal(raw.get("maxConcurrency"), defaults != null ? defaults.maxConcurrency() : 2);
        return new LlmEndpoint(id, label, enabled, provider, baseUrl, modelName, apiKey, organization, apiVersion, maxConcurrency);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static int intVal(Object o, int def) {
        if (o instanceof Number n) {
            return Math.max(1, n.intValue());
        }
        return def;
    }

    private static String maskKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
