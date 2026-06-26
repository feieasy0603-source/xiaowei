package com.xiaowei.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaowei.common.ApiResponse;
import com.xiaowei.integration.payment.AlipayPayNotifyService;
import com.xiaowei.integration.payment.AlipayTradeNotify;
import com.xiaowei.integration.payment.WechatPayNotifyService;
import com.xiaowei.integration.payment.WechatPayTransactionNotify;
import com.xiaowei.service.PaymentService;
import com.xiaowei.service.WalletRechargeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final WalletRechargeService walletRechargeService;
    private final WechatPayNotifyService wechatPayNotifyService;
    private final AlipayPayNotifyService alipayPayNotifyService;
    private final ObjectMapper objectMapper;

    @Value("${xiaowei.payment.mock-enabled:false}")
    private boolean mockEnabled;

    /**
     * 微信支付异步回调。
     * - 官方 V3：Wechatpay-* 请求头 + 加密 JSON 体，返回 {"code":"SUCCESS"}
     * - 开发演示：X-Pay-Secret + {orderNo, tradeNo, status}
     */
    @PostMapping("/callback/wechat")
    public ResponseEntity<Map<String, String>> wechatCallback(
            @RequestBody String rawBody,
            @RequestHeader(value = "Wechatpay-Signature", required = false) String signature,
            @RequestHeader(value = "Wechatpay-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial,
            @RequestHeader(value = "X-Pay-Secret", required = false) String devSecret
    ) {
        try {
            if (wechatPayNotifyService.isV3Notify(signature, serial)) {
                WechatPayTransactionNotify tx = wechatPayNotifyService.parseV3Notify(
                        rawBody, signature, timestamp, nonce, serial);
                if (tx != null) {
                    if (tx.outTradeNo().startsWith("WR")) {
                        walletRechargeService.completeWechatV3Recharge(tx);
                    } else {
                        paymentService.completeWechatV3Payment(tx);
                    }
                }
                return wechatOk();
            }
            if (!mockEnabled) {
                return wechatFail("开发支付回调已关闭");
            }
            Map<String, Object> body = objectMapper.readValue(
                    rawBody, new TypeReference<Map<String, Object>>() {});
            String orderNo = body.get("orderNo") != null ? String.valueOf(body.get("orderNo")) : "";
            if (orderNo.startsWith("WR")) {
                walletRechargeService.handleRechargeCallback(body, devSecret);
            } else {
                paymentService.handleWechatCallback(body, devSecret);
            }
            return wechatOk();
        } catch (Exception e) {
            log.error("支付回调处理失败: {}", e.getMessage());
            return wechatFail(e.getMessage());
        }
    }

    /**
     * 支付宝异步通知（application/x-www-form-urlencoded）。
     * 验签通过后返回纯文本 success。
     */
    @PostMapping("/callback/alipay")
    public String alipayCallback(@RequestParam Map<String, String> params) {
        try {
            AlipayTradeNotify notify = alipayPayNotifyService.parseNotify(params);
            if (notify != null && notify.isSuccess()) {
                if (notify.outTradeNo().startsWith("WR")) {
                    walletRechargeService.completeAlipayRecharge(notify);
                } else {
                    paymentService.completeAlipayPayment(notify);
                }
            }
            return "success";
        } catch (Exception e) {
            log.error("支付宝回调处理失败: {}", e.getMessage());
            return "failure";
        }
    }

    @PostMapping("/callback/mock")
    public ApiResponse<Map<String, Object>> mockCallback(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Pay-Secret", required = false) String secret
    ) {
        if (!mockEnabled) {
            return ApiResponse.fail(403, "模拟支付回调已关闭");
        }
        String orderNo = body.get("orderNo") != null ? String.valueOf(body.get("orderNo")) : "";
        if (orderNo.startsWith("WR")) {
            return ApiResponse.ok(walletRechargeService.handleRechargeCallback(body, secret));
        }
        return ApiResponse.ok(paymentService.handleWechatCallback(body, secret));
    }

    private static ResponseEntity<Map<String, String>> wechatOk() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", "SUCCESS");
        body.put("message", "成功");
        return ResponseEntity.ok(body);
    }

    private static ResponseEntity<Map<String, String>> wechatFail(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", "FAIL");
        body.put("message", message != null ? message : "失败");
        return ResponseEntity.status(500).body(body);
    }
}
