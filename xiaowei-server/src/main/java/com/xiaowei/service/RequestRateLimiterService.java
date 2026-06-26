package com.xiaowei.service;

import com.xiaowei.common.BusinessException;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易内存限流（单实例有效；多实例部署请换 Redis）。
 */
@Service
public class RequestRateLimiterService {

    private static final class Window {
        private final Deque<Long> hits = new ArrayDeque<>();
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public void check(String key, int maxRequests, int windowSeconds) {
        if (key == null || key.isBlank()) {
            key = "unknown";
        }
        long now = System.currentTimeMillis();
        long since = now - windowSeconds * 1000L;
        Window w = windows.computeIfAbsent(key, k -> new Window());
        synchronized (w) {
            while (!w.hits.isEmpty() && w.hits.peekFirst() < since) {
                w.hits.pollFirst();
            }
            if (w.hits.size() >= maxRequests) {
                throw new BusinessException(429, "请求过于频繁，请稍后再试");
            }
            w.hits.addLast(now);
        }
    }
}
