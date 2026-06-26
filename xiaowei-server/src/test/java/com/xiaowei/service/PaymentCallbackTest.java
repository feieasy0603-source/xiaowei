package com.xiaowei.service;

import com.xiaowei.common.BusinessException;
import com.xiaowei.domain.repository.PaymentRecordRepository;
import com.xiaowei.domain.repository.WalletRechargeOrderRepository;
import com.xiaowei.integration.payment.WechatPayClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class PaymentCallbackTest {

    @Mock
    private WalletService walletService;

    @Mock
    private WechatPayClient wechatPayClient;

    @Mock
    private WalletRechargeOrderRepository rechargeOrderRepository;

    @Mock
    private PaymentRecordRepository paymentRecordRepository;

    @InjectMocks
    private WalletRechargeService walletRechargeService;

    @Test
    void rechargeCallbackRejectsWrongSecret() {
        ReflectionTestUtils.setField(walletRechargeService, "callbackSecret", "expected-secret");

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> walletRechargeService.handleRechargeCallback(
                        Map.of("orderNo", "WR123", "status", "success", "tradeNo", "T1"),
                        "wrong-secret"));

        assertEquals("支付回调鉴权失败", ex.getMessage());
    }
}
