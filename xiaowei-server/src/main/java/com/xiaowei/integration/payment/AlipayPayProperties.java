package com.xiaowei.integration.payment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "xiaowei.payment.alipay")
public class AlipayPayProperties {

    /** 是否启用支付宝当面付（扫码）真实下单 */
    private boolean enabled = false;

    private String appId = "";
    /** 应用私钥 PEM（PKCS8，可含 BEGIN/END 行） */
    private String privateKeyPem = "";
    /** 支付宝公钥 PEM（非应用公钥） */
    private String alipayPublicKeyPem = "";
    /** OpenAPI 网关，正式/沙箱勿混用 */
    private String gatewayUrl = "https://openapi.alipay.com/gateway.do";
    /** 异步通知 URL（需 HTTPS 公网可达） */
    private String notifyUrl = "";

    public boolean isConfigured() {
        return enabled
                && appId != null && !appId.isBlank()
                && privateKeyPem != null && !privateKeyPem.isBlank()
                && alipayPublicKeyPem != null && !alipayPublicKeyPem.isBlank()
                && gatewayUrl != null && !gatewayUrl.isBlank()
                && notifyUrl != null && !notifyUrl.isBlank();
    }
}
