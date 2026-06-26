package com.xiaowei.integration.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaowei.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayPayClient {

    private static final String METHOD_PRECREATE = "alipay.trade.precreate";
    private static final String RESPONSE_NODE = "alipay_trade_precreate_response";
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlipayPayProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public record PrecreateResult(String qrCode, boolean mock, String outTradeNo) {}

    public PrecreateResult createPrecreateOrder(String outTradeNo, String subject, BigDecimal amountYuan) {
        if (!props.isConfigured()) {
            throw new BusinessException("支付宝未配置，请先补全应用参数或使用余额支付");
        }
        try {
            Map<String, Object> biz = new HashMap<>();
            biz.put("out_trade_no", outTradeNo);
            biz.put("total_amount", amountYuan.setScale(2, RoundingMode.HALF_UP).toPlainString());
            biz.put("subject", subject.length() > 120 ? subject.substring(0, 120) : subject);

            Map<String, String> params = new TreeMap<>();
            params.put("app_id", props.getAppId());
            params.put("method", METHOD_PRECREATE);
            params.put("format", "JSON");
            params.put("charset", "utf-8");
            params.put("sign_type", "RSA2");
            params.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FMT));
            params.put("version", "1.0");
            params.put("notify_url", props.getNotifyUrl());
            params.put("biz_content", objectMapper.writeValueAsString(biz));

            String signContent = AlipaySignHelper.buildSignContent(params);
            params.put("sign", AlipaySignHelper.sign(signContent, props.getPrivateKeyPem()));

            String formBody = buildFormBody(params);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(props.getGatewayUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.error("支付宝下单 HTTP 失败 {}: {}", response.statusCode(), response.body());
                throw new BusinessException("支付宝下单失败，请稍后重试或使用余额支付");
            }

            String body = response.body();
            String responseJson = AlipaySignHelper.extractResponseJson(body, RESPONSE_NODE);
            if (responseJson == null) {
                throw new BusinessException("支付宝响应格式异常");
            }
            JsonNode root = objectMapper.readTree(body);
            String sign = root.path("sign").asText(null);
            if (sign == null || !AlipaySignHelper.verify(responseJson, sign, props.getAlipayPublicKeyPem())) {
                throw new BusinessException("支付宝响应验签失败");
            }

            JsonNode node = objectMapper.readTree(responseJson);
            String code = node.path("code").asText("");
            if (!"10000".equals(code)) {
                String subMsg = node.path("sub_msg").asText(node.path("msg").asText("未知错误"));
                log.error("支付宝下单业务失败: {}", subMsg);
                throw new BusinessException("支付宝下单失败：" + subMsg);
            }
            String qrCode = node.path("qr_code").asText(null);
            if (qrCode == null || qrCode.isBlank()) {
                throw new BusinessException("支付宝未返回支付二维码");
            }
            return new PrecreateResult(qrCode, false, outTradeNo);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("支付宝当面付下单异常", e);
            throw new BusinessException("支付宝支付暂不可用：" + e.getMessage());
        }
    }

    private static String buildFormBody(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
