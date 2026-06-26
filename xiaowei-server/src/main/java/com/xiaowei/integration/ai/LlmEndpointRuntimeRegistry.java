package com.xiaowei.integration.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 按接入点 id 复用 Semaphore，配置热更新时尽量保留在途许可 */
public class LlmEndpointRuntimeRegistry {

    private final ConcurrentHashMap<String, LlmEndpointRuntime> runtimes = new ConcurrentHashMap<>();

    public List<LlmEndpointRuntime> sync(List<LlmEndpoint> endpoints) {
        Set<String> activeIds = new HashSet<>();
        List<LlmEndpointRuntime> out = new ArrayList<>();
        for (LlmEndpoint ep : endpoints) {
            activeIds.add(ep.id());
            LlmEndpointRuntime rt = runtimes.computeIfAbsent(ep.id(), k -> new LlmEndpointRuntime(ep));
            rt.sync(ep);
            out.add(rt);
        }
        runtimes.keySet().removeIf(id -> !activeIds.contains(id));
        return out;
    }

    public LlmEndpointRuntime find(String id) {
        return id == null ? null : runtimes.get(id);
    }
}
