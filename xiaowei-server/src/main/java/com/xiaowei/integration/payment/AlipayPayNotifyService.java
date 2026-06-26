package com.xiaowei.integration.payment;

import com.xiaowei.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlipayPayNotifyService {

    private final AlipayPayProperties props;

    public AlipayTradeNotify parseNotify(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            throw new BusinessException("支付宝通知为空");
        }
        if (!props.isConfigured()) {
            log.warn("支付宝未配置，忽略异步通知");
            return null;
        }

        Map<String, String> copy = new HashMap<>(params);
        String sign = copy.remove("sign");
        copy.remove("sign_type");

        String content = AlipaySignHelper.buildSignContent(copy);
        try {
            if (!AlipaySignHelper.verify(content, sign, props.getAlipayPublicKeyPem())) {
                throw new BusinessException("支付宝通知验签失败");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("支付宝通知验签异常", e);
            throw new BusinessException("支付宝通知验签失败");
        }

        String outTradeNo = copy.get("out_trade_no");
        String tradeNo = copy.get("trade_no");
        String tradeStatus = copy.get("trade_status");
        BigDecimal totalAmount = null;
        String amountStr = copy.get("total_amount");
        if (amountStr != null && !amountStr.isBlank()) {
            totalAmount = new BigDecimal(amountStr);
        }
        return new AlipayTradeNotify(outTradeNo, tradeNo, tradeStatus, totalAmount);
    }
}
