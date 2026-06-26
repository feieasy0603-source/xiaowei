package com.xiaowei.service;

import com.xiaowei.common.BusinessException;
import com.xiaowei.domain.entity.Order;
import com.xiaowei.domain.entity.PaymentRecord;
import com.xiaowei.domain.repository.OrderRepository;
import com.xiaowei.domain.repository.PaymentRecordRepository;
import com.xiaowei.integration.payment.AlipayPayClient;
import com.xiaowei.integration.payment.AlipayTradeNotify;
import com.xiaowei.integration.payment.WechatPayClient;
import com.xiaowei.integration.payment.WechatPayTransactionNotify;
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
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final OrderService orderService;
    private final WechatPayClient wechatPayClient;
    private final AlipayPayClient alipayPayClient;

    @Value("${xiaowei.payment.callback-secret:xiaowei-pay-dev-secret}")
    private String callbackSecret;

    @Value("${xiaowei.payment.mock-enabled:false}")
    private boolean mockEnabled;

    public Map<String, Object> createPrepay(Long userId, Long orderId, String method) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作");
        }
        if ("paid".equals(order.getPayStatus())) {
            throw new BusinessException("订单已支付");
        }
        String subject = "小微写作-" + order.getProductId();
        String payMethod = normalizePayMethod(method);
        Map<String, Object> m = new HashMap<>();
        m.put("orderId", order.getId());
        m.put("orderNo", order.getOrderNo());
        m.put("amount", order.getAmount());
        m.put("method", payMethod);
        m.put("expireSeconds", 300);
        if ("alipay".equals(payMethod)) {
            var precreate = alipayPayClient.createPrecreateOrder(order.getOrderNo(), subject, order.getAmount());
            m.put("prepayId", precreate.outTradeNo());
            m.put("qrContent", precreate.qrCode());
            m.put("mock", precreate.mock());
        } else {
            var nativeResult = wechatPayClient.createNativeOrder(order.getOrderNo(), subject, order.getAmount());
            m.put("prepayId", nativeResult.prepayId());
            m.put("qrContent", nativeResult.codeUrl());
            m.put("mock", nativeResult.mock());
        }
        return m;
    }

    @Transactional
    public Map<String, Object> handleWechatCallback(Map<String, Object> body, String secret) {
        if (!mockEnabled) {
            throw new BusinessException("开发支付回调已关闭");
        }
        return handleDevCallback(body, secret, "wechat", "WX");
    }

    @Transactional
    public Map<String, Object> handleAlipayCallback(Map<String, Object> body, String secret) {
        if (!mockEnabled) {
            throw new BusinessException("开发支付回调已关闭");
        }
        return handleDevCallback(body, secret, "alipay", "ALI");
    }

    /**
     * 微信 V3 官方异步通知入账（幂等）。
     */
    @Transactional
    public void completeWechatV3Payment(WechatPayTransactionNotify notify) {
        if (notify == null || !notify.isSuccess()) {
            return;
        }
        String orderNo = notify.outTradeNo();
        String tradeNo = notify.transactionId();
        if (orderNo == null || orderNo.isBlank() || tradeNo == null || tradeNo.isBlank()) {
            throw new BusinessException("微信通知缺少订单号");
        }
        Order order = orderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException("订单不存在: " + orderNo));
        int expectedFen = order.getAmount().multiply(BigDecimal.valueOf(100)).intValue();
        if (notify.totalAmountFen() > 0 && notify.totalAmountFen() != expectedFen) {
            throw new BusinessException("支付金额与订单不符");
        }
        if ("paid".equals(order.getPayStatus())) {
            return;
        }
        if (paymentRecordRepository.findByTradeNo(tradeNo).isPresent()) {
            return;
        }
        savePaymentRecord(order, "wechat", tradeNo, order.getAmount(), "success");
        orderService.completePayment(order.getId(), "wechat", tradeNo);
    }

    /**
     * 支付宝官方异步通知入账（幂等）。
     */
    @Transactional
    public void completeAlipayPayment(AlipayTradeNotify notify) {
        if (notify == null || !notify.isSuccess()) {
            return;
        }
        String orderNo = notify.outTradeNo();
        String tradeNo = notify.tradeNo();
        if (orderNo == null || orderNo.isBlank() || tradeNo == null || tradeNo.isBlank()) {
            throw new BusinessException("支付宝通知缺少订单号");
        }
        Order order = orderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException("订单不存在: " + orderNo));
        if (notify.totalAmount() != null) {
            BigDecimal expected = order.getAmount().setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal actual = notify.totalAmount().setScale(2, java.math.RoundingMode.HALF_UP);
            if (actual.compareTo(expected) != 0) {
                throw new BusinessException("支付金额与订单不符");
            }
        }
        if ("paid".equals(order.getPayStatus())) {
            return;
        }
        if (paymentRecordRepository.findByTradeNo(tradeNo).isPresent()) {
            return;
        }
        savePaymentRecord(order, "alipay", tradeNo, order.getAmount(), "success");
        orderService.completePayment(order.getId(), "alipay", tradeNo);
    }

    @Transactional
    public Map<String, Object> simulateWechatPay(Long userId, Long orderId) {
        if (!mockEnabled) {
            throw new BusinessException("模拟支付已关闭，请使用微信扫码支付");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作");
        }
        String tradeNo = "WX_MOCK_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> body = new HashMap<>();
        body.put("orderNo", order.getOrderNo());
        body.put("tradeNo", tradeNo);
        body.put("status", "success");
        return handleDevCallback(body, callbackSecret, "wechat", "WX");
    }

    @Transactional
    public Map<String, Object> simulateAlipayPay(Long userId, Long orderId) {
        if (!mockEnabled) {
            throw new BusinessException("模拟支付已关闭，请使用支付宝扫码支付");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("无权操作");
        }
        String tradeNo = "ALI_MOCK_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> body = new HashMap<>();
        body.put("orderNo", order.getOrderNo());
        body.put("tradeNo", tradeNo);
        body.put("status", "success");
        return handleDevCallback(body, callbackSecret, "alipay", "ALI");
    }

    private Map<String, Object> handleDevCallback(
            Map<String, Object> body,
            String secret,
            String payMethod,
            String tradePrefix
    ) {
        verifySecret(secret);
        String orderNo = require(body, "orderNo");
        String tradeNo = body.get("tradeNo") != null
                ? String.valueOf(body.get("tradeNo"))
                : tradePrefix + System.currentTimeMillis();
        String status = body.get("status") != null ? String.valueOf(body.get("status")) : "success";
        if (!"success".equalsIgnoreCase(status)) {
            throw new BusinessException("支付未成功");
        }
        Order order = orderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException("订单不存在"));
        if ("paid".equals(order.getPayStatus())) {
            return orderService.toDtoForOrder(order);
        }
        if (paymentRecordRepository.findByTradeNo(tradeNo).isPresent()) {
            return orderService.toDtoForOrder(order);
        }
        savePaymentRecord(order, payMethod, tradeNo, order.getAmount(), "success");
        return orderService.completePayment(order.getId(), payMethod, tradeNo);
    }

    private static String normalizePayMethod(String method) {
        if (method != null && "alipay".equalsIgnoreCase(method.trim())) {
            return "alipay";
        }
        return "wechat";
    }

    private void savePaymentRecord(Order order, String method, String tradeNo, BigDecimal amount, String status) {
        PaymentRecord record = new PaymentRecord();
        record.setUserId(order.getUserId());
        record.setOrderId(order.getId());
        record.setAmount(amount);
        record.setPayMethod(method);
        record.setTradeNo(tradeNo);
        record.setStatus(status);
        record.setCreatedAt(Instant.now());
        paymentRecordRepository.save(record);
    }

    private void verifySecret(String secret) {
        if (secret == null || !secret.equals(callbackSecret)) {
            throw new BusinessException("支付回调鉴权失败");
        }
    }

    private String require(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new BusinessException("缺少参数: " + key);
        }
        return String.valueOf(v);
    }
}
