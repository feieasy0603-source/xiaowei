package com.xiaowei.integration.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaowei.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信支付 V3 异步通知：验签 + 解密 resource。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatPayNotifyService {

    private static final long SIGNATURE_MAX_SKEW_SECONDS = 300;

    private final WechatPayProperties props;
    private final WechatPayClient wechatPayClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** serialNo -> (publicKey, expireAt) */
    private final Map<String, CachedKey> platformKeyCache = new ConcurrentHashMap<>();

    private record CachedKey(PublicKey key, Instant expireAt) {}

    public boolean isV3Notify(String signature, String serial) {
        return signature != null && !signature.isBlank()
                && serial != null && !serial.isBlank();
    }

    public WechatPayTransactionNotify parseV3Notify(
            String rawBody,
            String signature,
            String timestamp,
            String nonce,
            String serial
    ) {
        if (!props.isNotifyReady()) {
            throw new BusinessException("微信支付通知未配置（需 apiV3Key、serialNo 等）");
        }
        verifyTimestamp(timestamp);
        verifySignature(rawBody, signature, timestamp, nonce, serial);

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String eventType = root.path("event_type").asText("");
            if (!"TRANSACTION.SUCCESS".equals(eventType)) {
                log.info("忽略非成功支付事件: {}", eventType);
                return null;
            }
            JsonNode resource = root.path("resource");
            String plain = decryptResource(
                    resource.path("associated_data").asText(""),
                    resource.path("nonce").asText(""),
                    resource.path("ciphertext").asText(""));
            JsonNode tx = objectMapper.readTree(plain);
            return new WechatPayTransactionNotify(
                    tx.path("out_trade_no").asText(""),
                    tx.path("transaction_id").asText(""),
                    tx.path("trade_state").asText(""),
                    tx.path("amount").path("total").asInt(0),
                    tx.path("mchid").asText(""),
                    tx.path("appid").asText(""));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析微信 V3 通知失败", e);
            throw new BusinessException("微信通知解析失败");
        }
    }

    private void verifyTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            throw new BusinessException("缺少 Wechatpay-Timestamp");
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException("Wechatpay-Timestamp 无效");
        }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > SIGNATURE_MAX_SKEW_SECONDS) {
            throw new BusinessException("微信通知时间戳超出允许范围");
        }
    }

    private void verifySignature(
            String body,
            String signature,
            String timestamp,
            String nonce,
            String serial
    ) {
        if (signature == null || signature.isBlank()) {
            throw new BusinessException("缺少 Wechatpay-Signature");
        }
        if (nonce == null || nonce.isBlank()) {
            throw new BusinessException("缺少 Wechatpay-Nonce");
        }
        try {
            PublicKey key = resolvePlatformPublicKey(serial);
            String message = timestamp + "\n" + nonce + "\n" + body + "\n";
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(key);
            sig.update(message.getBytes(StandardCharsets.UTF_8));
            if (!sig.verify(Base64.getDecoder().decode(signature))) {
                throw new BusinessException("微信通知签名校验失败");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("微信通知验签异常", e);
            throw new BusinessException("微信通知验签失败");
        }
    }

    private PublicKey resolvePlatformPublicKey(String serial) throws Exception {
        if (serial == null || serial.isBlank()) {
            throw new BusinessException("缺少 Wechatpay-Serial");
        }
        CachedKey cached = platformKeyCache.get(serial);
        if (cached != null && cached.expireAt.isAfter(Instant.now())) {
            return cached.key();
        }
        String pem = wechatPayClient.fetchPlatformCertificatePem(serial);
        X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
        PublicKey key = cert.getPublicKey();
        Instant expire = cert.getNotAfter().toInstant();
        platformKeyCache.put(serial, new CachedKey(key, expire));
        return key;
    }

    private String decryptResource(String associatedData, String nonce, String ciphertext) throws Exception {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new BusinessException("通知 resource 为空");
        }
        byte[] keyBytes = props.getApiV3Key().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            throw new BusinessException("apiV3Key 须为 32 字节");
        }
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
}
