package com.xiaowei.api;

import com.xiaowei.common.ApiResponse;
import com.xiaowei.security.SecurityUtils;
import com.xiaowei.service.ProductPriceService;
import com.xiaowei.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductPriceService productPriceService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(productService.listProducts());
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String id) {
        return ApiResponse.ok(productService.getProduct(id));
    }

    @GetMapping("/{id}/quote")
    public ApiResponse<Map<String, Object>> quote(
            @PathVariable String id,
            @RequestParam(required = false) String degree,
            @RequestParam(required = false) Integer wordCount,
            @RequestParam(required = false) String modelType
    ) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(productPriceService.quoteDto(id, degree, wordCount, modelType));
    }
}
