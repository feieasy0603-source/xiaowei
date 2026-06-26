package com.xiaowei.integration.payment;

import java.math.BigDecimal;

public record AlipayTradeNotify(
        String outTradeNo,
        String tradeNo,
        String tradeStatus,
        BigDecimal totalAmount
) {
    public boolean isSuccess() {
        return "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
    }
}
