package com.xiaowei.api;

import com.xiaowei.common.ApiResponse;
import com.xiaowei.common.PageResult;
import com.xiaowei.security.SecurityUtils;
import com.xiaowei.service.OrderService;
import com.xiaowei.service.PaymentService;
import com.xiaowei.service.RequestRateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final RequestRateLimiterService rateLimiter;

    @Value("${xiaowei.rate-limit.lookup-max:30}")
    private int lookupMaxPerMinute;

    @Value("${xiaowei.payment.mock-enabled:false}")
    private boolean mockEnabled;

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(orderService.listByUser(uid, page, size));
    }

    /** 游客凭订单号或任务号查询，无需登录（按 IP 限流） */
    @GetMapping("/lookup")
    public ApiResponse<Map<String, Object>> lookup(
            HttpServletRequest request,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String jobNo
    ) {
        rateLimiter.check("lookup:" + clientIp(request), lookupMaxPerMinute, 60);
        return ApiResponse.ok(orderService.lookupPublic(orderNo, jobNo));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(orderService.getOrder(uid, id));
    }

    @GetMapping("/quote")
    public ApiResponse<Map<String, Object>> quote(
            @RequestParam String productId,
            @RequestParam(required = false) String degree,
            @RequestParam(required = false) Integer wordCount,
            @RequestParam(required = false) String modelType
    ) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(orderService.quoteForUser(uid, productId, degree, wordCount, modelType));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        Integer wordCount = body.get("wordCount") != null
                ? Integer.valueOf(String.valueOf(body.get("wordCount")))
                : null;
        return ApiResponse.ok(orderService.createOrder(
                uid,
                String.valueOf(body.get("productId")),
                body.get("paperId") != null ? String.valueOf(body.get("paperId")) : null,
                body.get("dCode") != null ? String.valueOf(body.get("dCode")) : null,
                body.get("degree") != null ? String.valueOf(body.get("degree")) : null,
                wordCount,
                body.get("modelType") != null ? String.valueOf(body.get("modelType")) : null
        ));
    }

    @PostMapping("/{id}/pay-balance")
    public ApiResponse<Map<String, Object>> payBalance(@PathVariable Long id) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(orderService.payWithBalance(uid, id));
    }

    @PostMapping("/{id}/prepay")
    public ApiResponse<Map<String, Object>> prepay(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        String method = body != null && body.get("method") != null
                ? String.valueOf(body.get("method"))
                : "wechat";
        return ApiResponse.ok(paymentService.createPrepay(uid, id, method));
    }

    /** 演示：模拟用户完成微信扫码支付（生产环境由回调接口触发） */
    @PostMapping("/{id}/pay-wechat-mock")
    public ApiResponse<Map<String, Object>> payWechatMock(@PathVariable Long id) {
        if (!mockEnabled) {
            return ApiResponse.fail(403, "模拟支付已关闭，请使用微信扫码支付");
        }
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(paymentService.simulateWechatPay(uid, id));
    }

    /** 演示：模拟用户完成支付宝扫码支付（生产环境由回调接口触发） */
    @PostMapping("/{id}/pay-alipay-mock")
    public ApiResponse<Map<String, Object>> payAlipayMock(@PathVariable Long id) {
        if (!mockEnabled) {
            return ApiResponse.fail(403, "模拟支付已关闭，请使用支付宝扫码支付");
        }
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(paymentService.simulateAlipayPay(uid, id));
    }
}
