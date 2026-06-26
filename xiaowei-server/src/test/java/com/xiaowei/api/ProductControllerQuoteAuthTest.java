package com.xiaowei.api;

import com.xiaowei.common.ApiResponse;
import com.xiaowei.service.ProductPriceService;
import com.xiaowei.service.ProductService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductControllerQuoteAuthTest {

    @Mock
    private ProductService productService;

    @Mock
    private ProductPriceService productPriceService;

    @InjectMocks
    private ProductController controller;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void quoteRequiresLogin() {
        ApiResponse<Map<String, Object>> res = controller.quote("paper_graduation", null, 8000, null);
        assertEquals(401, res.getCode());
    }

    @Test
    void quoteWithLoginReturnsPrice() {
        setCurrentUserId(42L);
        when(productPriceService.quoteDto(eq("paper_graduation"), isNull(), eq(8000), isNull()))
                .thenReturn(Map.of("amount", 99));

        ApiResponse<Map<String, Object>> res = controller.quote("paper_graduation", null, 8000, null);

        assertEquals(0, res.getCode());
        assertEquals(99, res.getData().get("amount"));
    }

    private static void setCurrentUserId(long userId) {
        var claims = Jwts.claims().add("userId", userId).build();
        var auth = new UsernamePasswordAuthenticationToken("user", null);
        auth.setDetails(claims);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
