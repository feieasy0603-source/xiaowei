package com.xiaowei.api;

import com.xiaowei.common.ApiResponse;
import com.xiaowei.common.BusinessException;
import com.xiaowei.service.OrderService;
import com.xiaowei.service.PaymentService;
import com.xiaowei.service.RequestRateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerLookupTest {

    @Mock
    private OrderService orderService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private RequestRateLimiterService rateLimiter;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private OrderController controller;

    @org.junit.jupiter.api.BeforeEach
    void init() {
        ReflectionTestUtils.setField(controller, "lookupMaxPerMinute", 30);
    }

    @Test
    void lookupAppliesRateLimitBeforeQuery() {
        doThrow(new BusinessException(429, "请求过于频繁，请稍后再试"))
                .when(rateLimiter).check(anyString(), anyInt(), anyInt());
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> controller.lookup(request, "O123", null));

        assertEquals(429, ex.getCode());
        verify(orderService, never()).lookupPublic(any(), any());
    }

    @Test
    void lookupDelegatesToServiceWhenAllowed() {
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(orderService.lookupPublic("O123", null)).thenReturn(Map.of("orderNo", "O123"));

        ApiResponse<Map<String, Object>> res = controller.lookup(request, "O123", null);

        assertEquals(0, res.getCode());
        verify(rateLimiter).check(eq("lookup:127.0.0.1"), eq(30), eq(60));
    }
}
