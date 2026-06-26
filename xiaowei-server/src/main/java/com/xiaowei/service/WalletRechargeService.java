package com.xiaowei.service;

import com.xiaowei.common.BusinessException;
import com.xiaowei.domain.entity.PaymentRecord;
import com.xiaowei.domain.entity.WalletRechargeOrder;
import com.xiaowei.domain.repository.PaymentRecordRepository;
import com.xiaowei.domain.repository.WalletRechargeOrderRepository;
import com.xiaowei.integration.payment.AlipayPayClient;
import com.xiaowei.integration.payment.AlipayTradeNotify;
import com.xiaowei.integration.payment.WechatPayClient;
import com.xiaowei.integration.payment.WechatPayTransactionNotify;
import com.xiaowei.util.BusinessIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletRechargeService {

    private final WalletService walletService;
    private final WechatPayClient wechatPayClient;
    private final AlipayPayClient alipayPayClient;
    private final WalletRechargeOrderRepository rechargeOrderRepository;
    private final PaymentRecordRepository paymentRecordRepository;

    @Value("${xiaowei.payment.callback-secret:xiaowei-pay-dev-secret}")
    private String callbackSecret;

    @Value("${xiaowei.payment.mock-enabled:false}")
    private boolean mockEnabled;

    @Transactional
    public Map<String, Object> createPrepay(Long userId, BigDecimal amount, String method) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("充值金额须大于 0");
        }
        String orderNo = BusinessIdGenerator.walletRechargeNo();
        WalletRechargeOrder order = new WalletRechargeOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setAmount(amount);
        order.setPayStatus("pending");
        rechargeOrderRepository.save(order);

        String payMethod = normalizePayMethod(method);
        String subject = "小微写作-余额充值";
        Map<String, Object> m = new HashMap<>();
        m.put("orderNo", orderNo);
        m.put("amount", amount);
        m.put("method", payMethod);
        m.put("expireSeconds", 300);
        if ("alipay".equals(payMethod)) {
            var precreate = alipayPayClient.createPrecreateOrder(orderNo, subject, amount);
            m.put("qrContent", precreate.qrCode());
            m.put("mock", precreate.mock());
        } else {
            var nativeResult = wechatPayClient.createNativeOrder(orderNo, subject, amount);
            m.put("qrContent", nativeResult.codeUrl());
            m.put("mock", nativeResult.mock());
        }
        return m;
    }

    @Transactional
    public Map<String, Object> confirmMock(Long userId, String orderNo) {
        if (!mockEnabled) {
            throw new BusinessException("模拟充值已关闭，请使用微信扫码");
        }
        WalletRechargeOrder order = rechargeOrderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException("充值订单不存在或已过期"));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作该充值订单");
        }
        if ("paid".equals(order.getPayStatus())) {
            return walletService.getWallet(userId);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("orderNo", orderNo);
        body.put("tradeNo", "WR_MOCK_" + UUID.randomUUID().toString().substring(0, 8));
        body.put("status", "success");
        return handleRechargeCallback(body, callbackSecret);
    }

    @Transactional
    public Map<String, Object> handleRechargeCallback(Map<String, Object> body, String secret) {
        if (!mockEnabled) {
            throw new BusinessException("开发充值回调已关闭");
        }
        if (secret == null || !secret.equals(callbackSecret)) {
            throw new BusinessException("支付回调鉴权失败");
        }
        String orderNo = String.valueOf(body.get("orderNo"));
        String status = body.get("status") != null ? String.valueOf(body.get("status")) : "success";
        if (!"success".equalsIgnoreCase(status)) {
            throw new BusinessException("支付未成功");
        }
        WalletRechargeOrder order = rechargeOrderRepository.findByOrderNoForUpdate(orderNo).orElse(null);
        if (order == null) {
            throw new BusinessException("充值订单不存在");
        }
        if ("paid".equals(order.getPayStatus())) {
            return walletService.getWallet(order.getUserId());
        }
        String tradeNo = body.get("tradeNo") != null
                ? String.valueOf(body.get("tradeNo"))
                : "WR_" + orderNo;
        String payMethod = resolveRechargePayMethod(tradeNo, body.get("payMethod"));
        markRechargePaid(order, tradeNo, payMethod);
        return walletService.getWallet(order.getUserId());
    }

    /**
     * 微信 V3 官方异步通知充值入账（幂等）。
     */
    @Transactional
    public void completeWechatV3Recharge(WechatPayTransactionNotify notify) {
        if (notify == null || !notify.isSuccess()) {
            return;
        }
        String orderNo = notify.outTradeNo();
        if (orderNo == null || !orderNo.startsWith("WR")) {
            throw new BusinessException("非余额充值订单: " + orderNo);
        }
        WalletRechargeOrder order = rechargeOrderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException("充值订单不存在: " + orderNo));
        int expectedFen = order.getAmount().multiply(BigDecimal.valueOf(100)).intValue();
        if (notify.totalAmountFen() > 0 && notify.totalAmountFen() != expectedFen) {
            throw new BusinessException("充值金额与订单不符");
        }
        if ("paid".equals(order.getPayStatus())) {
            return;
        }
        markRechargePaid(order, notify.transactionId(), "recharge");
    }

    /**
     * 支付宝官方异步通知充值入账（幂等）。
     */
    @Transactional
    public void completeAlipayRecharge(AlipayTradeNotify notify) {
        if (notify == null || !notify.isSuccess()) {
            return;
        }
        String orderNo = notify.outTradeNo();
        if (orderNo == null || !orderNo.startsWith("WR")) {
            throw new BusinessException("非余额充值订单: " + orderNo);
        }
        WalletRechargeOrder order = rechargeOrderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException("充值订单不存在: " + orderNo));
        if (notify.totalAmount() != null) {
            BigDecimal expected = order.getAmount().setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal actual = notify.totalAmount().setScale(2, java.math.RoundingMode.HALF_UP);
            if (actual.compareTo(expected) != 0) {
                throw new BusinessException("充值金额与订单不符");
            }
        }
        if ("paid".equals(order.getPayStatus())) {
            return;
        }
        markRechargePaid(order, notify.tradeNo(), "recharge_alipay");
    }

    private static String normalizePayMethod(String method) {
        if (method != null && "alipay".equalsIgnoreCase(method.trim())) {
            return "alipay";
        }
        return "wechat";
    }

    private static String resolveRechargePayMethod(String tradeNo, Object payMethodHint) {
        if (payMethodHint != null && !String.valueOf(payMethodHint).isBlank()) {
            return String.valueOf(payMethodHint);
        }
        if (tradeNo.startsWith("WR_MOCK_") || tradeNo.startsWith("ALI_MOCK_")) {
            return "recharge_mock";
        }
        if (tradeNo.startsWith("ALI")) {
            return "recharge_alipay";
        }
        return "recharge";
    }

    private void markRechargePaid(WalletRechargeOrder order, String tradeNo, String payMethod) {
        order.setPayStatus("paid");
        order.setPaidAt(Instant.now());
        rechargeOrderRepository.save(order);
        walletService.recharge(order.getUserId(), order.getAmount());
        recordRechargePayment(order, payMethod, tradeNo);
    }

    private void recordRechargePayment(WalletRechargeOrder order, String payMethod, String tradeNo) {
        if (tradeNo == null || tradeNo.isBlank()) {
            return;
        }
        if (paymentRecordRepository.findByTradeNo(tradeNo).isPresent()) {
            return;
        }
        PaymentRecord record = new PaymentRecord();
        record.setUserId(order.getUserId());
        record.setOrderId(null);
        record.setAmount(order.getAmount());
        record.setPayMethod(payMethod);
        record.setTradeNo(tradeNo);
        record.setStatus("success");
        record.setCreatedAt(Instant.now());
        paymentRecordRepository.save(record);
    }

    public Map<String, Object> getStatus(Long userId, String orderNo) {
        WalletRechargeOrder order = rechargeOrderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException("充值订单不存在或已过期"));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权查看该充值订单");
        }
        Map<String, Object> m = new HashMap<>();
        m.put("orderNo", orderNo);
        m.put("status", order.getPayStatus());
        m.put("amount", order.getAmount());
        return m;
    }

    /** 管理端：人工确认充值入账（线下已收款或 mock 环境补单） */
    @Transactional
    public Map<String, Object> adminConfirmPaid(Long rechargeId, String adminRef) {
        WalletRechargeOrder order = rechargeOrderRepository.findByIdForUpdate(rechargeId)
                .orElseThrow(() -> new BusinessException("充值订单不存在"));
        if ("paid".equals(order.getPayStatus())) {
            return walletRechargeToAdminMap(order);
        }
        if (!"pending".equals(order.getPayStatus())) {
            throw new BusinessException("仅待支付订单可确认入账");
        }
        String ref = adminRef != null && !adminRef.isBlank()
                ? adminRef.replaceAll("[^a-zA-Z0-9_-]", "")
                : "admin";
        String tradeNo = "WR_ADMIN_" + ref + "_" + rechargeId + "_" + System.currentTimeMillis();
        markRechargePaid(order, tradeNo, "recharge_admin");
        return walletRechargeToAdminMap(order);
    }

    /** 管理端：取消 stuck 待支付充值单 */
    @Transactional
    public Map<String, Object> adminCancel(Long rechargeId) {
        WalletRechargeOrder order = rechargeOrderRepository.findById(rechargeId)
                .orElseThrow(() -> new BusinessException("充值订单不存在"));
        if ("paid".equals(order.getPayStatus())) {
            throw new BusinessException("已支付订单不可取消");
        }
        if ("cancelled".equals(order.getPayStatus())) {
            return walletRechargeToAdminMap(order);
        }
        order.setPayStatus("cancelled");
        rechargeOrderRepository.save(order);
        return walletRechargeToAdminMap(order);
    }

    private Map<String, Object> walletRechargeToAdminMap(WalletRechargeOrder o) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", o.getId());
        m.put("orderNo", o.getOrderNo());
        m.put("userId", o.getUserId());
        m.put("amount", o.getAmount());
        m.put("payStatus", o.getPayStatus());
        m.put("paidAt", o.getPaidAt());
        m.put("createdAt", o.getCreatedAt());
        return m;
    }
}
