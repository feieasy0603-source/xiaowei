package com.xiaowei.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaowei.domain.entity.AiRuntimeConfig;
import com.xiaowei.domain.repository.AiRuntimeConfigRepository;
import com.xiaowei.integration.ai.AiProviderPreset;
import com.xiaowei.integration.ai.LlmEndpoint;
import com.xiaowei.integration.ai.LlmEndpointPool;
import com.xiaowei.integration.ai.LlmEndpointRuntime;
import com.xiaowei.integration.ai.LlmEndpointRuntimeRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiConfigService {

    private final AiRuntimeConfigRepository runtimeConfigRepository;
    private final AiModelUsageService aiModelUsageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${xiaowei.ai.mock:false}")
    private boolean defaultMock;

    @Value("${xiaowei.ai.allow-mock:false}")
    private boolean allowMock;

    @Value("${xiaowei.ai.provider:openai}")
    private String defaultProvider;

    @Value("${xiaowei.ai.base-url:}")
    private String defaultBaseUrl;

    @Value("${xiaowei.ai.model-name:gpt-4o-mini}")
    private String defaultModelName;

    @Value("${xiaowei.ai.timeout-ms:120000}")
    private int defaultTimeoutMs;

    @Value("${xiaowei.ai.max-concurrency:4}")
    private int defaultMaxConcurrency;

    @Value("${xiaowei.ai.global-concurrency-mode:auto}")
    private String defaultGlobalConcurrencyMode;

    @Value("${xiaowei.ai.slot-acquire-timeout-ms:90000}")
    private long defaultSlotAcquireTimeoutMs;

    @Value("${xiaowei.ai.temperature:0.7}")
    private double defaultTemperature;

    private final AtomicReference<Map<String, Object>> config = new AtomicReference<>();
    private final ThreadLocal<LlmEndpoint> activeEndpoint = new ThreadLocal<>();
    private final LlmEndpointRuntimeRegistry endpointRuntimeRegistry = new LlmEndpointRuntimeRegistry();
    private volatile LlmEndpointPool endpointPool = new LlmEndpointPool(
            List.of(),
            LlmEndpointPool.STRATEGY_ROUND_ROBIN,
            4,
            LlmEndpointPool.GLOBAL_CONCURRENCY_AUTO,
            90_000L,
            Map.of()
    );

    @PostConstruct
    public void init() {
        runtimeConfigRepository.findById(1L).ifPresent(row -> {
            try {
                Map<String, Object> loaded = objectMapper.readValue(
                        row.getConfigJson(), new TypeReference<>() {});
                if (loaded != null && !loaded.isEmpty()) {
                    Map<String, Object> normalized = normalize(loaded);
                    config.set(normalized);
                    refreshPool();
                    if (shouldRewriteStoredMockConfig(loaded, normalized)) {
                        persist(normalized);
                    }
                }
            } catch (Exception ignored) {
                /* use yaml defaults */
            }
        });
        getConfig();
    }

    public boolean isMock() {
        String provider = String.valueOf(getConfig().getOrDefault("provider", AiProviderPreset.MOCK));
        return AiProviderPreset.MOCK.equals(provider);
    }

    public Optional<LlmEndpoint> currentEndpoint() {
        return Optional.ofNullable(activeEndpoint.get());
    }

    public LlmEndpoint requireEndpoint() {
        return currentEndpoint().orElseGet(this::legacyEndpoint);
    }

    public String modelForTaskType(String taskType) {
        LlmEndpoint ep = requireEndpoint();
        return resolveModelName(taskType, ep);
    }

    public Map<String, Object> getConfig() {
        Map<String, Object> current = config.get();
        if (current != null) {
            return current;
        }
        config.set(buildDefaults());
        refreshPool();
        return config.get();
    }

    public Map<String, Object> getConfigForAdmin() {
        Map<String, Object> c = new HashMap<>(getConfig());
        String key = String.valueOf(c.getOrDefault("apiKey", ""));
        c.put("apiKeySet", key != null && !key.isBlank());
        c.put("apiKey", maskKey(key));
        c.put("providers", providerPresetsForAdmin());
        c.put("modelPools", modelPoolsForAdmin(c));
        c.put("poolStrategy", c.getOrDefault("poolStrategy", LlmEndpointPool.STRATEGY_ROUND_ROBIN));
        c.put("globalConcurrencyMode", c.getOrDefault("globalConcurrencyMode", defaultGlobalConcurrencyMode));
        c.put("slotAcquireTimeoutMs", c.getOrDefault("slotAcquireTimeoutMs", defaultSlotAcquireTimeoutMs));
        c.put("taskTypeEndpoints", c.getOrDefault("taskTypeEndpoints", Map.of()));
        c.put("effectiveGlobalMax", endpointPool.effectiveGlobalMax());
        c.put("multiModelEnabled", endpointPool.hasMultipleEnabled());
        c.put("tokenStats", aiModelUsageService.summary());
        return c;
    }

    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> updateConfig(Map<String, Object> body) {
        Map<String, Object> next = new HashMap<>(getConfig());
        if (body.containsKey("mock")) {
            if (Boolean.TRUE.equals(body.get("mock")) && !allowMock) {
                throw new com.xiaowei.common.BusinessException("Mock 模式已被关闭");
            }
            next.put("mock", Boolean.TRUE.equals(body.get("mock")));
        }
        if (body.get("provider") != null) {
            if (AiProviderPreset.MOCK.equals(String.valueOf(body.get("provider"))) && !allowMock) {
                throw new com.xiaowei.common.BusinessException("Mock 模式已被关闭");
            }
            next.put("provider", String.valueOf(body.get("provider")));
        }
        if (body.get("baseUrl") != null) {
            next.put("baseUrl", String.valueOf(body.get("baseUrl")));
        }
        if (body.get("modelName") != null) {
            next.put("modelName", String.valueOf(body.get("modelName")));
        }
        if (body.get("organization") != null) {
            next.put("organization", String.valueOf(body.get("organization")));
        }
        if (body.get("apiVersion") != null) {
            next.put("apiVersion", String.valueOf(body.get("apiVersion")));
        }
        if (body.get("apiKey") != null) {
            String key = String.valueOf(body.get("apiKey"));
            if (!key.isBlank() && !key.contains("****")) {
                next.put("apiKey", key);
            }
        }
        if (body.get("timeoutMs") != null) {
            next.put("timeoutMs", ((Number) body.get("timeoutMs")).intValue());
        }
        if (body.get("maxConcurrency") != null) {
            next.put("maxConcurrency", ((Number) body.get("maxConcurrency")).intValue());
        }
        if (body.get("globalConcurrencyMode") != null) {
            next.put("globalConcurrencyMode", String.valueOf(body.get("globalConcurrencyMode")));
        }
        if (body.get("slotAcquireTimeoutMs") != null) {
            next.put("slotAcquireTimeoutMs", ((Number) body.get("slotAcquireTimeoutMs")).longValue());
        }
        if (body.get("taskTypeEndpoints") != null && body.get("taskTypeEndpoints") instanceof Map<?, ?> map) {
            next.put("taskTypeEndpoints", map);
        }
        if (body.get("temperature") != null) {
            next.put("temperature", ((Number) body.get("temperature")).doubleValue());
        }
        if (body.get("poolStrategy") != null) {
            next.put("poolStrategy", String.valueOf(body.get("poolStrategy")));
        }
        if (body.get("taskTypeModels") != null) {
            Object raw = body.get("taskTypeModels");
            if (raw instanceof Map<?, ?> map) {
                next.put("taskTypeModels", map);
            } else if (raw instanceof String s && !s.isBlank()) {
                try {
                    next.put("taskTypeModels", objectMapper.readValue(s, new TypeReference<Map<String, String>>() {}));
                } catch (Exception ignored) {
                    /* keep */
                }
            }
        }
        if (body.get("modelPools") != null) {
            next.put("modelPools", mergeModelPools(
                    (List<Map<String, Object>>) next.getOrDefault("modelPools", List.of()),
                    body.get("modelPools")));
        }
        next = normalize(next);
        config.set(next);
        refreshPool();
        persist(next);
        return getConfigForAdmin();
    }

    public List<LlmEndpoint> enabledEndpoints() {
        return endpointPool.enabled();
    }

    public List<LlmEndpoint> allEndpoints() {
        return endpointPool.all();
    }

    public LlmEndpoint findEndpointById(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return endpointPool.all().stream()
                .filter(e -> id.equals(e.id()))
                .findFirst()
                .orElse(null);
    }

    public <T> T runOnEndpoint(LlmEndpoint endpoint, Supplier<T> action) {
        if (isMock()) {
            return action.get();
        }
        boolean globalAcquired = false;
        boolean endpointAcquired = false;
        activeEndpoint.set(endpoint);
        try {
            endpointPool.acquireGlobal();
            globalAcquired = true;
            endpointPool.acquireEndpoint(endpoint);
            endpointAcquired = true;
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new com.xiaowei.common.BusinessException("AI 调用被中断");
        } finally {
            if (endpointAcquired) {
                endpointPool.releaseEndpoint(endpoint);
            }
            if (globalAcquired) {
                endpointPool.releaseGlobal();
            }
            activeEndpoint.remove();
        }
    }

    public String modelForEndpoint(String taskType, LlmEndpoint endpoint) {
        return resolveModelName(taskType, endpoint);
    }

    public <T> T withLlmLimit(Supplier<T> action) {
        return withLlmLimit(null, action);
    }

    public <T> T withLlmLimit(String taskType, Supplier<T> action) {
        if (isMock()) {
            return action.get();
        }
        Set<String> tried = new HashSet<>();
        Set<String> unhealthy = unhealthyEndpointIds();
        Exception lastError = null;
        while (true) {
            Set<String> exclude = new HashSet<>(tried);
            exclude.addAll(unhealthy);
            List<LlmEndpoint> candidates;
            try {
                candidates = endpointPool.selectCandidates(taskType, routedModelName(taskType), exclude);
            } catch (IllegalStateException e) {
                if (lastError != null) {
                    throw wrapLlmError(lastError);
                }
                throw new com.xiaowei.common.BusinessException(e.getMessage());
            }
            LlmEndpoint endpoint = null;
            for (LlmEndpoint c : candidates) {
                if (!tried.contains(c.id())) {
                    endpoint = c;
                    break;
                }
            }
            if (endpoint == null) {
                if (lastError != null) {
                    throw wrapLlmError(lastError);
                }
                throw new com.xiaowei.common.BusinessException("无可用 AI 模型接入点");
            }
            tried.add(endpoint.id());
            try {
                return runOnEndpoint(endpoint, action);
            } catch (com.xiaowei.common.BusinessException e) {
                lastError = e;
                if (tried.size() >= countEnabledExcluding(unhealthy)) {
                    throw e;
                }
            } catch (RuntimeException e) {
                lastError = e;
                if (tried.size() >= countEnabledExcluding(unhealthy)) {
                    throw e;
                }
            }
        }
    }

    public Map<String, Object> poolRuntimeStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("effectiveGlobalMax", endpointPool.effectiveGlobalMax());
        m.put("poolStrategy", getConfig().getOrDefault("poolStrategy", LlmEndpointPool.STRATEGY_ROUND_ROBIN));
        m.put("globalConcurrencyMode", getConfig().getOrDefault("globalConcurrencyMode", defaultGlobalConcurrencyMode));
        m.put("endpoints", endpointPool.toAdminStatus());
        m.put("mock", isMock());
        return m;
    }

    private int countEnabledExcluding(Set<String> unhealthy) {
        return (int) endpointPool.enabled().stream()
                .filter(ep -> unhealthy == null || !unhealthy.contains(ep.id()))
                .count();
    }

    @SuppressWarnings("unchecked")
    private Set<String> unhealthyEndpointIds() {
        Object raw = getConfig().get("modelPools");
        if (!(raw instanceof List<?> list)) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            if ("fail".equals(String.valueOf(map.get("healthStatus")))) {
                Object id = map.get("id");
                if (id != null) {
                    ids.add(String.valueOf(id));
                }
            }
        }
        return ids;
    }

    private com.xiaowei.common.BusinessException wrapLlmError(Exception e) {
        if (e instanceof com.xiaowei.common.BusinessException be) {
            return be;
        }
        String msg = e.getMessage() != null ? e.getMessage() : "AI 调用失败";
        return new com.xiaowei.common.BusinessException(msg);
    }

    /**
     * 关闭 Mock：保留现有模型池，仅将 provider 切出 mock。
     * 若未配置任何带 Key 的启用模型，则拒绝（避免上线后全部失败）。
     */
    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> applyProductionMode() {
        if (countEnabledPoolsWithKey() < 1) {
            throw new com.xiaowei.common.BusinessException(
                    "请先在 AI 模型池添加至少 1 个已启用且配置 API Key 的模型");
        }
        Map<String, Object> next = new HashMap<>(getConfig());
        List<Map<String, Object>> pools = (List<Map<String, Object>>) next.getOrDefault("modelPools", List.of());
        Map<String, Object> first = pools.stream()
                .filter(p -> !Boolean.FALSE.equals(p.get("enabled")))
                .filter(p -> hasApiKey(p))
                .findFirst()
                .orElseThrow(() -> new com.xiaowei.common.BusinessException("无可用模型池"));
        next.put("mock", false);
        next.put("provider", String.valueOf(first.getOrDefault("provider", AiProviderPreset.DEEPSEEK)));
        next.put("baseUrl", String.valueOf(first.getOrDefault("baseUrl", "")));
        next.put("modelName", String.valueOf(first.getOrDefault("modelName", "")));
        next = normalize(next);
        config.set(next);
        refreshPool();
        persist(next);
        Map<String, Object> out = new HashMap<>();
        out.put("message", "已关闭 AI Mock，当前使用真实模型池");
        out.put("config", getConfigForAdmin());
        return out;
    }

    @SuppressWarnings("unchecked")
    private int countEnabledPoolsWithKey() {
        List<Map<String, Object>> pools = (List<Map<String, Object>>)
                getConfig().getOrDefault("modelPools", List.of());
        int n = 0;
        for (Map<String, Object> p : pools) {
            if (!Boolean.FALSE.equals(p.get("enabled")) && hasApiKey(p)) {
                n++;
            }
        }
        return n;
    }

    private boolean hasApiKey(Map<String, Object> pool) {
        Object key = pool.get("apiKey");
        return key != null && !String.valueOf(key).isBlank();
    }

    /** 重置为 YAML 指定的真实模型默认配置；Mock 默认不再开放。 */
    public synchronized Map<String, Object> resetToRecommended() {
        Map<String, Object> next = buildDefaults();
        if (allowMock) {
            next.put("provider", AiProviderPreset.MOCK);
            next.put("mock", true);
            next.put("modelName", "mock");
            next.put("baseUrl", "");
        } else {
            next.put("provider", nonMockDefaultProvider());
            next.put("mock", false);
            next.put("baseUrl", AiProviderPreset.find(nonMockDefaultProvider()).defaultBaseUrl());
            next.put("modelName", defaultModelName);
        }
        next.put("apiKey", "");
        next.put("taskTypeModels", new HashMap<String, String>());
        next.put("modelPools", List.of());
        next.put("poolStrategy", LlmEndpointPool.STRATEGY_ROUND_ROBIN);
        next.put("globalConcurrencyMode", LlmEndpointPool.GLOBAL_CONCURRENCY_AUTO);
        config.set(normalize(next));
        refreshPool();
        persist(next);
        return getConfigForAdmin();
    }

    /** 将探测结果写回 modelPools 并刷新运行时池 */
    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> applyEndpointHealth(List<Map<String, Object>> probeRows) {
        if (probeRows == null || probeRows.isEmpty()) {
            return getConfigForAdmin();
        }
        Map<String, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> row : probeRows) {
            if (row.get("id") != null) {
                byId.put(String.valueOf(row.get("id")), row);
            }
        }
        Map<String, Object> next = new HashMap<>(getConfig());
        List<Map<String, Object>> pools = (List<Map<String, Object>>) next.getOrDefault("modelPools", List.of());
        List<Map<String, Object>> updated = new ArrayList<>();
        for (Map<String, Object> row : pools) {
            Map<String, Object> copy = new HashMap<>(row);
            String id = String.valueOf(copy.getOrDefault("id", ""));
            Map<String, Object> probe = byId.get(id);
            if (probe != null) {
                copy.put("healthStatus", probe.getOrDefault("healthStatus", "unknown"));
                copy.put("healthMessage", probe.getOrDefault("healthMessage", ""));
                copy.put("healthCheckedAt", Instant.now().toString());
            }
            updated.add(copy);
        }
        next.put("modelPools", updated);
        next = normalize(next);
        config.set(next);
        refreshPool();
        persist(next);
        return getConfigForAdmin();
    }

    public double temperature() {
        return ((Number) getConfig().getOrDefault("temperature", defaultTemperature)).doubleValue();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> modelPoolsForAdmin(Map<String, Object> c) {
        Object raw = c.get("modelPools");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        Map<String, LlmEndpoint> live = new HashMap<>();
        for (LlmEndpoint ep : endpointPool.all()) {
            live.put(ep.id(), ep);
        }
        Map<String, Map<String, Object>> tokenById = aiModelUsageService.listForAdmin().stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.get("endpointId")),
                        row -> row,
                        (a, b) -> a
                ));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            map.forEach((k, v) -> row.put(String.valueOf(k), v));
            String id = String.valueOf(row.getOrDefault("id", ""));
            LlmEndpoint defaults = legacyEndpointFrom(c);
            LlmEndpoint ep = LlmEndpoint.fromMap(row, defaults);
            Map<String, Object> admin = ep.toAdminMap(true);
            admin.put("healthStatus", row.getOrDefault("healthStatus", "unknown"));
            admin.put("healthMessage", row.getOrDefault("healthMessage", ""));
            admin.put("healthCheckedAt", row.getOrDefault("healthCheckedAt", ""));
            LlmEndpoint runtime = live.get(id);
            if (runtime != null) {
                LlmEndpointRuntime rt = endpointRuntimeRegistry.find(id);
                admin.put("availablePermits", rt != null ? rt.availablePermits() : runtime.availablePermits());
            }
            Map<String, Object> usage = tokenById.get(id);
            if (usage != null) {
                admin.put("promptTokens", usage.get("promptTokens"));
                admin.put("completionTokens", usage.get("completionTokens"));
                admin.put("totalTokens", usage.get("totalTokens"));
                admin.put("requestCount", usage.get("requestCount"));
            } else {
                admin.put("promptTokens", 0);
                admin.put("completionTokens", 0);
                admin.put("totalTokens", 0);
                admin.put("requestCount", 0);
            }
            rows.add(admin);
        }
        return rows;
    }

    private void refreshPool() {
        Map<String, Object> c = getConfig();
        LlmEndpoint legacy = legacyEndpointFrom(c);
        List<LlmEndpoint> endpoints = buildRuntimeEndpoints(c, legacy);
        List<LlmEndpointRuntime> runtimes = endpointRuntimeRegistry.sync(endpoints);
        String strategy = String.valueOf(c.getOrDefault("poolStrategy", LlmEndpointPool.STRATEGY_ROUND_ROBIN));
        int globalMax = ((Number) c.getOrDefault("maxConcurrency", defaultMaxConcurrency)).intValue();
        String globalMode = String.valueOf(c.getOrDefault("globalConcurrencyMode", defaultGlobalConcurrencyMode));
        long slotTimeout = ((Number) c.getOrDefault("slotAcquireTimeoutMs", defaultSlotAcquireTimeoutMs)).longValue();
        @SuppressWarnings("unchecked")
        Map<String, String> taskTypeEndpoints = (Map<String, String>) c.getOrDefault("taskTypeEndpoints", Map.of());
        endpointPool = new LlmEndpointPool(
                runtimes,
                strategy,
                globalMax,
                globalMode,
                slotTimeout,
                taskTypeEndpoints
        );
    }

    private String routedModelName(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            return null;
        }
        Map<String, Object> c = getConfig();
        @SuppressWarnings("unchecked")
        Map<String, String> routes = (Map<String, String>) c.get("taskTypeModels");
        if (routes == null) {
            return null;
        }
        return routes.get(taskType);
    }

    private List<LlmEndpoint> buildRuntimeEndpoints(Map<String, Object> c, LlmEndpoint legacy) {
        List<LlmEndpoint> pools = parseModelPools(c, legacy);
        return pools.isEmpty() ? List.of(legacy) : pools;
    }

    private LlmEndpoint legacyEndpoint() {
        return legacyEndpointFrom(getConfig());
    }

    private LlmEndpoint legacyEndpointFrom(Map<String, Object> c) {
        String provider = String.valueOf(c.getOrDefault("provider", AiProviderPreset.OPENAI));
        int max = ((Number) c.getOrDefault("maxConcurrency", defaultMaxConcurrency)).intValue();
        return new LlmEndpoint(
                "primary",
                "主配置",
                true,
                provider,
                String.valueOf(c.getOrDefault("baseUrl", "")),
                String.valueOf(c.getOrDefault("modelName", defaultModelName)),
                String.valueOf(c.getOrDefault("apiKey", "")),
                String.valueOf(c.getOrDefault("organization", "")),
                String.valueOf(c.getOrDefault("apiVersion", "2024-02-15-preview")),
                max
        );
    }

    @SuppressWarnings("unchecked")
    private List<LlmEndpoint> parseModelPools(Map<String, Object> c, LlmEndpoint defaults) {
        Object raw = c.get("modelPools");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<LlmEndpoint> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new HashMap<>();
                map.forEach((k, v) -> row.put(String.valueOf(k), v));
                LlmEndpoint ep = LlmEndpoint.fromMap(row, defaults);
                String provider = ep.provider();
                String model = sanitizeModelName(provider, ep.modelName());
                out.add(new LlmEndpoint(
                        ep.id(),
                        ep.label(),
                        ep.enabled(),
                        provider,
                        ep.baseUrl(),
                        model,
                        ep.apiKey(),
                        ep.organization(),
                        ep.apiVersion(),
                        ep.maxConcurrency()
                ));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mergeModelPools(
            List<Map<String, Object>> existing,
            Object incoming
    ) {
        if (!(incoming instanceof List<?> list)) {
            return existing;
        }
        Map<String, Map<String, Object>> oldById = new HashMap<>();
        for (Map<String, Object> row : existing) {
            if (row.get("id") != null) {
                oldById.put(String.valueOf(row.get("id")), row);
            }
        }
        List<Map<String, Object>> merged = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            map.forEach((k, v) -> row.put(String.valueOf(k), v));
            String id = row.get("id") != null ? String.valueOf(row.get("id")).trim() : "";
            if (id.isBlank()) {
                id = UUID.randomUUID().toString();
                row.put("id", id);
            }
            Map<String, Object> prev = oldById.get(id);
            if (prev != null) {
                String key = String.valueOf(row.getOrDefault("apiKey", ""));
                if (key.isBlank() || key.contains("****")) {
                    row.put("apiKey", prev.get("apiKey"));
                }
                if (!row.containsKey("healthStatus") && prev.get("healthStatus") != null) {
                    row.put("healthStatus", prev.get("healthStatus"));
                    row.put("healthMessage", prev.get("healthMessage"));
                    row.put("healthCheckedAt", prev.get("healthCheckedAt"));
                }
            }
            merged.add(row);
        }
        return merged;
    }

    private String resolveModelName(String taskType, LlmEndpoint endpoint) {
        Map<String, Object> c = getConfig();
        @SuppressWarnings("unchecked")
        Map<String, String> routes = (Map<String, String>) c.get("taskTypeModels");
        if (taskType != null && routes != null && routes.containsKey(taskType)) {
            String routed = routes.get(taskType);
            if (routed != null && !routed.isBlank()) {
                return routed;
            }
        }
        String model = endpoint.modelName();
        if (model != null && !model.isBlank()) {
            return model;
        }
        return String.valueOf(c.getOrDefault("modelName", defaultModelName));
    }

    private void persist(Map<String, Object> next) {
        try {
            String json = objectMapper.writeValueAsString(next);
            AiRuntimeConfig row = runtimeConfigRepository.findById(1L).orElseGet(() -> {
                AiRuntimeConfig cfg = new AiRuntimeConfig();
                cfg.setId(1L);
                return cfg;
            });
            row.setConfigJson(json);
            row.setUpdatedAt(Instant.now());
            runtimeConfigRepository.save(row);
        } catch (Exception ignored) {
            /* 内存配置仍生效 */
        }
    }

    private Map<String, Object> buildDefaults() {
        String provider = defaultProvider != null && !defaultProvider.isBlank()
                ? defaultProvider
                : (defaultMock ? AiProviderPreset.MOCK : AiProviderPreset.OPENAI);
        if (!allowMock && (defaultMock || AiProviderPreset.MOCK.equals(provider))) {
            provider = nonMockDefaultProvider();
        }
        AiProviderPreset.Preset preset = AiProviderPreset.find(provider);
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("mock", allowMock && defaultMock);
        defaults.put("provider", provider);
        defaults.put("baseUrl", defaultBaseUrl != null && !defaultBaseUrl.isBlank()
                ? defaultBaseUrl : preset.defaultBaseUrl());
        defaults.put("modelName", defaultModelName);
        defaults.put("apiKey", "");
        defaults.put("organization", "");
        defaults.put("apiVersion", "2024-02-15-preview");
        defaults.put("timeoutMs", defaultTimeoutMs);
        defaults.put("maxConcurrency", defaultMaxConcurrency);
        defaults.put("temperature", defaultTemperature);
        defaults.put("taskTypeModels", new HashMap<String, String>());
        defaults.put("modelPools", List.of());
        defaults.put("poolStrategy", LlmEndpointPool.STRATEGY_ROUND_ROBIN);
        defaults.put("globalConcurrencyMode", defaultGlobalConcurrencyMode);
        defaults.put("slotAcquireTimeoutMs", defaultSlotAcquireTimeoutMs);
        defaults.put("taskTypeEndpoints", new HashMap<String, String>());
        return normalize(defaults);
    }

    private Map<String, Object> normalize(Map<String, Object> c) {
        Map<String, Object> n = new HashMap<>(c);
        String provider = String.valueOf(n.getOrDefault("provider", AiProviderPreset.MOCK));
        if (!allowMock && (Boolean.TRUE.equals(n.get("mock")) || AiProviderPreset.MOCK.equals(provider))) {
            log.warn("检测到数据库 AI Mock 配置，但当前已禁用 Mock；自动切换为真实模型默认配置");
            provider = nonMockDefaultProvider();
            n.put("provider", provider);
            n.put("mock", false);
            n.put("baseUrl", AiProviderPreset.find(provider).defaultBaseUrl());
            n.put("modelName", defaultModelName);
            n.put("apiKey", "");
        }
        AiProviderPreset.Preset preset = AiProviderPreset.find(provider);
        if (n.get("baseUrl") == null || String.valueOf(n.get("baseUrl")).isBlank()) {
            n.put("baseUrl", preset.defaultBaseUrl());
        }
        if (n.get("modelName") == null || String.valueOf(n.get("modelName")).isBlank()) {
            n.put("modelName", preset.defaultModel());
        }
        if (!n.containsKey("taskTypeModels") || n.get("taskTypeModels") == null) {
            n.put("taskTypeModels", new HashMap<String, String>());
        }
        if (!n.containsKey("modelPools") || n.get("modelPools") == null) {
            n.put("modelPools", List.of());
        }
        if (!n.containsKey("poolStrategy") || String.valueOf(n.get("poolStrategy")).isBlank()) {
            n.put("poolStrategy", LlmEndpointPool.STRATEGY_ROUND_ROBIN);
        }
        if (!n.containsKey("globalConcurrencyMode") || String.valueOf(n.get("globalConcurrencyMode")).isBlank()) {
            n.put("globalConcurrencyMode", defaultGlobalConcurrencyMode);
        }
        if (!n.containsKey("slotAcquireTimeoutMs") || n.get("slotAcquireTimeoutMs") == null) {
            n.put("slotAcquireTimeoutMs", defaultSlotAcquireTimeoutMs);
        }
        if (!n.containsKey("taskTypeEndpoints") || n.get("taskTypeEndpoints") == null) {
            n.put("taskTypeEndpoints", new HashMap<String, String>());
        }
        if (!allowMock) {
            removeMockPools(n);
            n.put("mock", false);
        } else if (AiProviderPreset.MOCK.equals(provider)) {
            n.put("mock", true);
        }
        n.put("modelName", sanitizeModelName(provider, String.valueOf(n.get("modelName"))));
        migrateLegacyToPool(n);
        syncLegacyFieldsFromPool(n);
        return n;
    }

    private boolean shouldRewriteStoredMockConfig(Map<String, Object> loaded, Map<String, Object> normalized) {
        if (allowMock) {
            return false;
        }
        Object loadedProvider = loaded.get("provider");
        Object normalizedProvider = normalized.get("provider");
        return Boolean.TRUE.equals(loaded.get("mock"))
                || AiProviderPreset.MOCK.equals(String.valueOf(loadedProvider))
                || !String.valueOf(loadedProvider).equals(String.valueOf(normalizedProvider));
    }

    private String nonMockDefaultProvider() {
        if (defaultProvider != null && !defaultProvider.isBlank()
                && !AiProviderPreset.MOCK.equals(defaultProvider)) {
            return defaultProvider;
        }
        return AiProviderPreset.OPENAI;
    }

    private List<Map<String, Object>> providerPresetsForAdmin() {
        List<Map<String, Object>> providers = AiProviderPreset.toAdminList();
        if (allowMock) {
            return providers;
        }
        return providers.stream()
                .filter(p -> !AiProviderPreset.MOCK.equals(String.valueOf(p.get("id"))))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private void removeMockPools(Map<String, Object> n) {
        Object raw = n.get("modelPools");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            if (AiProviderPreset.MOCK.equals(String.valueOf(map.get("provider")))) {
                continue;
            }
            filtered.add((Map<String, Object>) map);
        }
        n.put("modelPools", filtered);
    }

    @SuppressWarnings("unchecked")
    private void migrateLegacyToPool(Map<String, Object> n) {
        List<Map<String, Object>> pools = (List<Map<String, Object>>) n.get("modelPools");
        if (pools != null && !pools.isEmpty()) {
            return;
        }
        if (Boolean.TRUE.equals(n.get("mock"))) {
            return;
        }
        String provider = String.valueOf(n.getOrDefault("provider", AiProviderPreset.MOCK));
        if (AiProviderPreset.MOCK.equals(provider)) {
            return;
        }
        String key = String.valueOf(n.getOrDefault("apiKey", ""));
        if (key.isBlank()) {
            return;
        }
        Map<String, Object> row = new HashMap<>();
        row.put("id", "primary");
        row.put("label", "默认模型");
        row.put("enabled", true);
        row.put("provider", provider);
        row.put("baseUrl", n.get("baseUrl"));
        row.put("modelName", n.get("modelName"));
        row.put("apiKey", key);
        row.put("organization", n.get("organization"));
        row.put("apiVersion", n.get("apiVersion"));
        row.put("maxConcurrency", n.getOrDefault("maxConcurrency", defaultMaxConcurrency));
        n.put("modelPools", List.of(row));
    }

    @SuppressWarnings("unchecked")
    private void syncLegacyFieldsFromPool(Map<String, Object> n) {
        List<Map<String, Object>> pools = (List<Map<String, Object>>) n.get("modelPools");
        if (pools == null || pools.isEmpty()) {
            return;
        }
        Map<String, Object> first = pools.stream()
                .filter(row -> row.get("enabled") == null || Boolean.TRUE.equals(row.get("enabled")))
                .findFirst()
                .orElse(pools.get(0));
        if (first.get("provider") != null) {
            n.put("provider", first.get("provider"));
        }
        if (first.get("baseUrl") != null) {
            n.put("baseUrl", first.get("baseUrl"));
        }
        if (first.get("modelName") != null) {
            n.put("modelName", first.get("modelName"));
        }
        if (first.get("apiKey") != null && !String.valueOf(first.get("apiKey")).contains("****")) {
            n.put("apiKey", first.get("apiKey"));
        }
    }

    private String sanitizeModelName(String provider, String modelName) {
        if (modelName == null || modelName.isBlank() || "mock".equals(modelName)) {
            return AiProviderPreset.find(provider).defaultModel();
        }
        if (AiProviderPreset.DEEPSEEK.equals(provider)) {
            return sanitizeDeepSeekModel(modelName);
        }
        return modelName;
    }

    private String sanitizeDeepSeekModel(String modelName) {
        String m = modelName.trim().toLowerCase();
        return switch (m) {
            case "deepseek-v4-flash", "deepseek-v4-pro",
                 "deepseek-chat", "deepseek-reasoner" -> m;
            default -> {
                if (m.startsWith("deepseek-v4")) {
                    yield m;
                }
                yield AiProviderPreset.find(AiProviderPreset.DEEPSEEK).defaultModel();
            }
        };
    }

    private String maskKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
