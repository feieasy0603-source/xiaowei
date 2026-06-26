package com.xiaowei.integration.payment;

import java.math.BigDecimal;

/**
 * 微信支付 V3 通知解密后的交易摘要。
 */
public record WechatPayTransactionNotify(
        String outTradeNo,
        String transactionId,
        String tradeState,
        int totalAmountFen,
        String mchId,
        String appId
) {
    public boolean isSuccess() {
        return "SUCCESS".equals(tradeState);
    }

    public BigDecimal totalAmountYuan() {
        return BigDecimal.valueOf(totalAmountFen, 2);
    }
}
