package com.xiaowei.integration.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "xiaowei.payment.wechat")
public class WechatPayProperties {

    /** 是否启用微信 Native 真实下单 */
    private boolean enabled = false;

    private String appId = "";
    private String mchId = "";
    private String apiV3Key = "";
    private String serialNo = "";
    /** PEM 格式商户私钥（可含 BEGIN/END 行） */
    private String privateKeyPem = "";
    /** 支付结果回调 URL（需 HTTPS） */
    private String notifyUrl = "";

    public boolean isConfigured() {
        return enabled
                && appId != null && !appId.isBlank()
                && mchId != null && !mchId.isBlank()
                && serialNo != null && !serialNo.isBlank()
                && apiV3Key != null && apiV3Key.length() == 32
                && privateKeyPem != null && !privateKeyPem.isBlank()
                && notifyUrl != null && !notifyUrl.isBlank();
    }

    /** 是否可处理微信 V3 异步通知（验签 + 解密） */
    public boolean isNotifyReady() {
        return isConfigured();
    }
}
