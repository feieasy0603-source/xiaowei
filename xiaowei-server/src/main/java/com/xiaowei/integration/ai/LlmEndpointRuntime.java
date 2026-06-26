package com.xiaowei.integration.ai;

import java.util.concurrent.Semaphore;

/** 运行时接入点：配置 + 可复用的并发信号量（热更新时按 id 保留） */
public final class LlmEndpointRuntime {

    private volatile LlmEndpoint endpoint;
    private final Semaphore semaphore;
    private int configuredMax;

    public LlmEndpointRuntime(LlmEndpoint endpoint) {
        this.endpoint = endpoint;
        this.configuredMax = Math.max(1, endpoint.maxConcurrency());
        this.semaphore = new Semaphore(this.configuredMax);
    }

    public LlmEndpoint endpoint() {
        return endpoint;
    }

    public Semaphore semaphore() {
        return semaphore;
    }

    public int availablePermits() {
        return semaphore.availablePermits();
    }

    public void sync(LlmEndpoint next) {
        int newMax = Math.max(1, next.maxConcurrency());
        if (newMax != configuredMax) {
            adjustPermits(newMax - configuredMax);
            configuredMax = newMax;
        }
        this.endpoint = next;
    }

    private void adjustPermits(int delta) {
        if (delta > 0) {
            semaphore.release(delta);
            return;
        }
        int need = -delta;
        while (need > 0) {
            if (semaphore.tryAcquire()) {
                need--;
            } else {
                break;
            }
        }
    }
}
