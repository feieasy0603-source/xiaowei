package com.xiaowei.integration.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaowei.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WechatPayClient {

    private final WechatPayProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public record NativeOrderResult(String codeUrl, boolean mock, String prepayId) {}

    public record RefundResult(String refundId, boolean mock) {}

    public NativeOrderResult createNativeOrder(String outTradeNo, String description, BigDecimal amountYuan) {
        if (!props.isConfigured()) {
            throw new BusinessException("微信支付未配置，请先补全商户参数或使用余额支付");
        }
        try {
            int totalFen = amountYuan.multiply(BigDecimal.valueOf(100)).intValue();
            Map<String, Object> body = new HashMap<>();
            body.put("appid", props.getAppId());
            body.put("mchid", props.getMchId());
            body.put("description", description.length() > 120 ? description.substring(0, 120) : description);
            body.put("out_trade_no", outTradeNo);
            body.put("notify_url", props.getNotifyUrl());
            body.put("amount", Map.of("total", totalFen, "currency", "CNY"));

            String bodyJson = objectMapper.writeValueAsString(body);
            String path = "/v3/pay/transactions/native";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mch.weixin.qq.com" + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", buildAuthorization("POST", path, bodyJson))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.error("微信下单失败 {}: {}", response.statusCode(), response.body());
                throw new BusinessException("微信下单失败，请稍后重试或使用余额支付");
            }
            JsonNode json = objectMapper.readTree(response.body());
            String codeUrl = json.path("code_url").asText(null);
            if (codeUrl == null || codeUrl.isBlank()) {
                throw new BusinessException("微信未返回支付二维码");
            }
            return new NativeOrderResult(codeUrl, false, outTradeNo);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("微信 Native 下单异常", e);
            throw new BusinessException("微信支付暂不可用：" + e.getMessage());
        }
    }

    /** 申请退款（全额）。 */
    public RefundResult createRefund(
            String outTradeNo,
            String outRefundNo,
            int totalFen,
            int refundFen,
            String reason
    ) {
        if (!props.isConfigured()) {
            throw new BusinessException("微信支付未配置，无法发起微信退款");
        }
        if (refundFen <= 0 || refundFen > totalFen) {
            throw new BusinessException("退款金额无效");
        }
        try {
            Map<String, Object> amount = new HashMap<>();
            amount.put("refund", refundFen);
            amount.put("total", totalFen);
            amount.put("currency", "CNY");

            Map<String, Object> body = new HashMap<>();
            body.put("out_trade_no", outTradeNo);
            body.put("out_refund_no", outRefundNo);
            body.put("reason", reason.length() > 80 ? reason.substring(0, 80) : reason);
            body.put("amount", amount);

            String bodyJson = objectMapper.writeValueAsString(body);
            String path = "/v3/refund/domestic/refunds";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mch.weixin.qq.com" + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", buildAuthorization("POST", path, bodyJson))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.error("微信退款失败 {}: {}", response.statusCode(), response.body());
                throw new BusinessException("微信退款失败，请稍后重试或在商户平台操作");
            }
            JsonNode json = objectMapper.readTree(response.body());
            String refundId = json.path("refund_id").asText(outRefundNo);
            return new RefundResult(refundId, false);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("微信退款异常", e);
            throw new BusinessException("微信退款暂不可用：" + e.getMessage());
        }
    }

    /**
     * 拉取并解密微信平台证书（按 serial 匹配），返回 PEM 文本。
     */
    public String fetchPlatformCertificatePem(String targetSerial) throws Exception {
        String path = "/v3/certificates";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mch.weixin.qq.com" + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Authorization", buildAuthorization("GET", path, ""))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            log.error("拉取微信平台证书失败 {}: {}", response.statusCode(), response.body());
            throw new BusinessException("拉取微信平台证书失败");
        }
        JsonNode data = objectMapper.readTree(response.body()).path("data");
        if (!data.isArray()) {
            throw new BusinessException("微信平台证书响应无效");
        }
        for (JsonNode item : data) {
            String serial = item.path("serial_no").asText("");
            if (!targetSerial.equals(serial)) {
                continue;
            }
            JsonNode enc = item.path("encrypt_certificate");
            String plain = decryptAesGcm(
                    props.getApiV3Key(),
                    enc.path("associated_data").asText(""),
                    enc.path("nonce").asText(""),
                    enc.path("ciphertext").asText(""));
            return plain;
        }
        throw new BusinessException("未找到 serial 对应的微信平台证书: " + targetSerial);
    }

    static String decryptAesGcm(String apiV3Key, String associatedData, String nonce, String ciphertext)
            throws Exception {
        byte[] keyBytes = apiV3Key.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8));
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        if (associatedData != null && !associatedData.isEmpty()) {
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        }
        byte[] plain = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
        return new String(plain, StandardCharsets.UTF_8);
    }

    private String buildAuthorization(String method, String path, String body) throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String message = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
        String signature = sign(message);
        String token = String.format(
                "mchid=\"%s\",nonce_str=\"%s\",signature=\"%s\",timestamp=\"%s\",serial_no=\"%s\"",
                props.getMchId(), nonce, signature, timestamp, props.getSerialNo());
        return "WECHATPAY2-SHA256-RSA2048 " + token;
    }

    private String sign(String message) throws Exception {
        PrivateKey key = loadPrivateKey(props.getPrivateKeyPem());
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(key);
        sig.update(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sig.sign());
    }

    static PrivateKey loadPrivateKey(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }
}
