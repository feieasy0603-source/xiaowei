package com.xiaowei.integration.ai;

import com.xiaowei.common.BusinessException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多模型池调度：并发任务在多个接入点间轮询或按空闲度分配。
 */
public class LlmEndpointPool {

    public static final String STRATEGY_ROUND_ROBIN = "round_robin";
    public static final String STRATEGY_LEAST_BUSY = "least_busy";

    public static final String GLOBAL_CONCURRENCY_AUTO = "auto";
    public static final String GLOBAL_CONCURRENCY_MANUAL = "manual";

    private final List<LlmEndpointRuntime> runtimes;
    private final String strategy;
    private final Semaphore globalSemaphore;
    private final int effectiveGlobalMax;
    private final long slotAcquireTimeoutMs;
    private final Map<String, String> taskTypeEndpoints;
    private final AtomicInteger roundRobin = new AtomicInteger(0);

    public LlmEndpointPool(
            List<LlmEndpointRuntime> runtimes,
            String strategy,
            int configuredGlobalMax,
            String globalConcurrencyMode,
            long slotAcquireTimeoutMs,
            Map<String, String> taskTypeEndpoints
    ) {
        this.runtimes = runtimes != null ? List.copyOf(runtimes) : List.of();
        this.strategy = strategy != null && !strategy.isBlank() ? strategy : STRATEGY_ROUND_ROBIN;
        this.effectiveGlobalMax = resolveEffectiveGlobalMax(configuredGlobalMax, globalConcurrencyMode);
        this.globalSemaphore = new Semaphore(Math.max(1, this.effectiveGlobalMax));
        this.slotAcquireTimeoutMs = Math.max(1000L, slotAcquireTimeoutMs);
        this.taskTypeEndpoints = taskTypeEndpoints != null ? Map.copyOf(taskTypeEndpoints) : Map.of();
    }

    public static int resolveEffectiveGlobalMax(
            List<LlmEndpoint> enabled,
            int configuredGlobalMax,
            String globalConcurrencyMode
    ) {
        int configured = Math.max(1, configuredGlobalMax);
        if (!GLOBAL_CONCURRENCY_AUTO.equals(globalConcurrencyMode)) {
            return configured;
        }
        if (enabled == null || enabled.isEmpty()) {
            return configured;
        }
        int sum = enabled.stream().mapToInt(LlmEndpoint::maxConcurrency).sum();
        return Math.max(1, sum);
    }

    private int resolveEffectiveGlobalMax(int configuredGlobalMax, String globalConcurrencyMode) {
        return resolveEffectiveGlobalMax(enabled(), configuredGlobalMax, globalConcurrencyMode);
    }

    public int effectiveGlobalMax() {
        return effectiveGlobalMax;
    }

    public List<LlmEndpoint> all() {
        return runtimes.stream().map(LlmEndpointRuntime::endpoint).toList();
    }

    public List<LlmEndpoint> enabled() {
        return runtimes.stream()
                .filter(rt -> rt.endpoint().enabled())
                .map(LlmEndpointRuntime::endpoint)
                .toList();
    }

    public boolean hasMultipleEnabled() {
        return enabled().size() > 1;
    }

    public LlmEndpoint select(String taskType, String routedModelName) {
        return selectCandidates(taskType, routedModelName, Set.of()).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("无可用 AI 模型接入点，请在管理端配置模型池"));
    }

    public List<LlmEndpoint> selectCandidates(String taskType, String routedModelName, Set<String> excludeIds) {
        List<LlmEndpointRuntime> active = activeRuntimes(excludeIds);
        if (active.isEmpty()) {
            throw new IllegalStateException("无可用 AI 模型接入点，请在管理端配置模型池");
        }
        if (active.size() == 1) {
            return List.of(active.get(0).endpoint());
        }
        LlmEndpointRuntime pinned = selectByTaskType(taskType, active);
        if (pinned != null) {
            return orderWithFallback(active, pinned);
        }
        if (routedModelName != null && !routedModelName.isBlank()) {
            for (LlmEndpointRuntime rt : active) {
                if (routedModelName.equals(rt.endpoint().modelName())) {
                    return orderWithFallback(active, rt);
                }
            }
        }
        if (STRATEGY_LEAST_BUSY.equals(strategy)) {
            LlmEndpointRuntime best = active.stream()
                    .max(Comparator.comparingInt(LlmEndpointRuntime::availablePermits))
                    .orElse(active.get(0));
            return orderWithFallback(active, best);
        }
        int idx = Math.floorMod(roundRobin.getAndIncrement(), active.size());
        return orderWithFallback(active, active.get(idx));
    }

    private List<LlmEndpoint> orderWithFallback(List<LlmEndpointRuntime> active, LlmEndpointRuntime primary) {
        List<LlmEndpoint> ordered = new ArrayList<>();
        ordered.add(primary.endpoint());
        for (LlmEndpointRuntime rt : active) {
            if (!rt.endpoint().id().equals(primary.endpoint().id())) {
                ordered.add(rt.endpoint());
            }
        }
        return ordered;
    }

    private List<LlmEndpointRuntime> activeRuntimes(Set<String> excludeIds) {
        return runtimes.stream()
                .filter(rt -> rt.endpoint().enabled())
                .filter(rt -> excludeIds == null || !excludeIds.contains(rt.endpoint().id()))
                .toList();
    }

    private LlmEndpointRuntime selectByTaskType(String taskType, List<LlmEndpointRuntime> active) {
        if (taskType == null || taskType.isBlank() || taskTypeEndpoints.isEmpty()) {
            return null;
        }
        String endpointId = taskTypeEndpoints.get(taskType);
        if (endpointId == null || endpointId.isBlank()) {
            return null;
        }
        return active.stream()
                .filter(rt -> endpointId.equals(rt.endpoint().id()))
                .findFirst()
                .orElse(null);
    }

    public void acquireGlobal() throws InterruptedException {
        if (!globalSemaphore.tryAcquire(slotAcquireTimeoutMs, TimeUnit.MILLISECONDS)) {
            throw new BusinessException(
                    "AI 全局并发已满，请稍后再试（已等待 " + slotAcquireTimeoutMs + "ms）");
        }
    }

    public void releaseGlobal() {
        globalSemaphore.release();
    }

    public void acquireEndpoint(LlmEndpoint endpoint) throws InterruptedException {
        LlmEndpointRuntime rt = runtimeFor(endpoint);
        if (!rt.semaphore().tryAcquire(slotAcquireTimeoutMs, TimeUnit.MILLISECONDS)) {
            throw new BusinessException(
                    "模型「" + endpoint.label() + "」并发已满，请稍后再试（已等待 " + slotAcquireTimeoutMs + "ms）");
        }
    }

    public void releaseEndpoint(LlmEndpoint endpoint) {
        runtimeFor(endpoint).semaphore().release();
    }

    private LlmEndpointRuntime runtimeFor(LlmEndpoint endpoint) {
        return runtimes.stream()
                .filter(rt -> rt.endpoint().id().equals(endpoint.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未知模型接入点: " + endpoint.id()));
    }

    public List<Map<String, Object>> toAdminStatus() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LlmEndpointRuntime rt : runtimes) {
            LlmEndpoint ep = rt.endpoint();
            Map<String, Object> row = ep.toAdminMap(true);
            row.put("availablePermits", rt.availablePermits());
            row.put("maxConcurrency", ep.maxConcurrency());
            rows.add(row);
        }
        return rows;
    }
}
