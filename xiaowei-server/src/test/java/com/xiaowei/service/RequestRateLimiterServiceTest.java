package com.xiaowei.service;

import com.xiaowei.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestRateLimiterServiceTest {

    private final RequestRateLimiterService limiter = new RequestRateLimiterService();

    @Test
    void allowsRequestsWithinLimit() {
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiter.check("test-key", 5, 60));
        }
    }

    @Test
    void rejectsWhenExceedingLimit() {
        for (int i = 0; i < 3; i++) {
            limiter.check("burst-key", 3, 60);
        }
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> limiter.check("burst-key", 3, 60));
        assertEquals(429, ex.getCode());
    }
}
