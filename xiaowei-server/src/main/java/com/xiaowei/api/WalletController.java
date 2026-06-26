package com.xiaowei.api;

import com.xiaowei.common.ApiResponse;
import com.xiaowei.security.SecurityUtils;
import com.xiaowei.service.GiftCodeService;
import com.xiaowei.service.RequestRateLimiterService;
import com.xiaowei.service.VipQuotaService;
import com.xiaowei.service.WalletRechargeService;
import com.xiaowei.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final WalletRechargeService walletRechargeService;
    private final VipQuotaService vipQuotaService;
    private final GiftCodeService giftCodeService;
    private final RequestRateLimiterService rateLimiter;

    @Value("${xiaowei.wallet.allow-direct-recharge:false}")
    private boolean allowDirectRecharge;

    @Value("${xiaowei.payment.mock-enabled:false}")
    private boolean mockEnabled;

    @GetMapping
    public ApiResponse<Map<String, Object>> get() {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(walletService.getWallet(uid));
    }

    @GetMapping("/logs")
    public ApiResponse<Map<String, Object>> logs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(walletService.listLogs(uid, page, size));
    }

    @GetMapping("/quota")
    public ApiResponse<List<Map<String, Object>>> quota() {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(vipQuotaService.getUserQuotaSummary(uid));
    }

    /** 仅开发/内测；生产应走 prepay + 支付回调 */
    @PostMapping("/recharge")
    public ApiResponse<Map<String, Object>> recharge(@RequestBody Map<String, Object> body) {
        if (!allowDirectRecharge) {
            return ApiResponse.fail(403, "请通过充值订单完成支付");
        }
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        return ApiResponse.ok(walletService.recharge(uid, amount));
    }

    @PostMapping("/recharge/prepay")
    public ApiResponse<Map<String, Object>> rechargePrepay(@RequestBody Map<String, Object> body) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        String method = body.get("method") != null ? String.valueOf(body.get("method")) : "wechat";
        return ApiResponse.ok(walletRechargeService.createPrepay(uid, amount, method));
    }

    @GetMapping("/recharge/status")
    public ApiResponse<Map<String, Object>> rechargeStatus(@RequestParam String orderNo) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        return ApiResponse.ok(walletRechargeService.getStatus(uid, orderNo));
    }

    @PostMapping("/recharge/confirm-mock")
    public ApiResponse<Map<String, Object>> confirmRechargeMock(@RequestBody Map<String, Object> body) {
        if (!mockEnabled) {
            return ApiResponse.fail(403, "模拟充值已关闭，请使用微信扫码");
        }
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        String orderNo = String.valueOf(body.get("orderNo"));
        return ApiResponse.ok(walletRechargeService.confirmMock(uid, orderNo));
    }

    @PostMapping("/redeem-gift")
    public ApiResponse<Map<String, Object>> redeemGift(@RequestBody Map<String, String> body) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) return ApiResponse.fail(401, "未登录");
        rateLimiter.check("wallet:redeem:" + uid, 5, 60);
        return ApiResponse.ok(giftCodeService.redeem(uid, body.get("code")));
    }
}
