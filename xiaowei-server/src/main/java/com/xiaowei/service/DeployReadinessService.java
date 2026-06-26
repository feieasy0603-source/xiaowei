package com.xiaowei.service;

import com.xiaowei.integration.payment.AlipayPayProperties;
import com.xiaowei.integration.payment.WechatPayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class DeployReadinessService {

    private static final String DEFAULT_JWT_HINT =
            "xiaowei-dev-secret-change-in-production-min-32-chars";

    private final Environment environment;
    private final AiConfigService aiConfigService;
    private final WechatPayProperties wechatPayProperties;
    private final AlipayPayProperties alipayPayProperties;

    @Value("${xiaowei.jwt.secret}")
    private String jwtSecret;

    @Value("${xiaowei.payment.callback-secret:xiaowei-pay-dev-secret}")
    private String payCallbackSecret;

    @Value("${xiaowei.payment.mock-enabled:false}")
    private boolean paymentMockEnabled;

    @Value("${xiaowei.referral.frontend-base-url:}")
    private String referralFrontendBaseUrl;

    @Value("${xiaowei.wallet.allow-direct-recharge:false}")
    private boolean allowDirectRecharge;

    @Value("${xiaowei.ai.mock:false}")
    private boolean yamlAiMock;

    @Value("${xiaowei.cors.allowed-origins:}")
    private String corsAllowedOrigins;

    public Map<String, Object> readiness() {
        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(checkProdProfile());
        checks.add(checkAiRuntimeMock());
        checks.add(checkYamlAiMock());
        checks.add(checkModelPool());
        checks.add(checkJwtSecret());
        checks.add(checkPayCallbackSecret());
        checks.add(checkPaymentMock());
        checks.add(checkWechatPay());
        checks.add(checkAlipayPay());
        checks.add(checkWalletDirectRecharge());
        checks.add(checkReferralFrontendUrl());
        checks.add(checkCorsProd());

        int passed = 0;
        int warn = 0;
        for (Map<String, Object> c : checks) {
            String st = String.valueOf(c.get("status"));
            if ("ok".equals(st)) passed++;
            else if ("warn".equals(st)) warn++;
        }

        boolean productionReady = checks.stream().noneMatch(c -> "fail".equals(c.get("status")))
                && !aiConfigService.isMock()
                && !isWeakJwt()
                && countEnabledModelsWithKey() > 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checks", checks);
        result.put("passed", passed);
        result.put("warn", warn);
        result.put("total", checks.size());
        result.put("productionReady", productionReady);
        result.put("activeProfiles", List.of(environment.getActiveProfiles()));
        result.put("envTemplates", envTemplates());
        result.put("nginxSnippet", nginxSnippet());
        return result;
    }

    public Map<String, Object> applyProductionAiMode() {
        return aiConfigService.applyProductionMode();
    }

    /** 支付 Mock 需通过环境变量关闭并重启；返回生产配置指引 */
    public Map<String, Object> applyDisablePaymentMock() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", "请在服务器设置 PAYMENT_MOCK_ENABLED=false 并重启后端；运行时无法仅靠数据库热关闭支付 Mock");
        m.put("env", "PAYMENT_MOCK_ENABLED=false");
        m.put("currentMockEnabled", paymentMockEnabled);
        m.put("restartedRequired", true);
        return m;
    }

    private Map<String, Object> checkProdProfile() {
        String[] profiles = environment.getActiveProfiles();
        boolean prod = Arrays.stream(profiles).anyMatch(p -> "prod".equalsIgnoreCase(p));
        if (prod) {
            return check("prod_profile", "Spring Profile 含 prod", "ok",
                    "已激活: " + String.join(", ", profiles),
                    null);
        }
        return check("prod_profile", "Spring Profile 含 prod", "warn",
                "当前: " + (profiles.length == 0 ? "(default)" : String.join(", ", profiles)),
                "启动时设置 SPRING_PROFILES_ACTIVE=prod,mysql");
    }

    private Map<String, Object> checkAiRuntimeMock() {
        if (!aiConfigService.isMock()) {
            return check("ai_runtime_mock", "AI 运行模式（数据库配置）", "ok",
                    "已使用真实模型池", null);
        }
        return check("ai_runtime_mock", "AI 运行模式（数据库配置）", "fail",
                "当前为 Mock 演示，生成内容为模板",
                "在本向导第 4 步点击「关闭 AI Mock」，或在 AI 模型池切换为真实模型");
    }

    private Map<String, Object> checkYamlAiMock() {
        if (!yamlAiMock) {
            return check("yaml_ai_mock", "application 默认 AI Mock", "ok",
                    "YAML 已关闭 mock（prod 配置生效）", null);
        }
        return check("yaml_ai_mock", "application 默认 AI Mock", "warn",
                "YAML 仍为 mock:true（若 DB 已配置真实模型则运行时以 DB 为准）",
                "使用 prod profile 或设置 XIAOWEI_AI_MOCK=false");
    }

    private Map<String, Object> checkModelPool() {
        int n = countEnabledModelsWithKey();
        if (n > 0) {
            return check("model_pool", "模型池 API Key", "ok",
                    "已启用且配置密钥的模型: " + n + " 个", null);
        }
        if (aiConfigService.isMock()) {
            return check("model_pool", "模型池 API Key", "warn",
                    "Mock 模式下无需密钥", "关闭 Mock 后至少配置 1 个带 API Key 的模型");
        }
        return check("model_pool", "模型池 API Key", "fail",
                "无可用模型（需启用并填写 API Key）",
                "前往 AI 模型池 → 添加模型 → 保存并测试");
    }

    private Map<String, Object> checkJwtSecret() {
        if (!isWeakJwt()) {
            return check("jwt_secret", "JWT 密钥强度", "ok", "已使用自定义密钥", null);
        }
        return check("jwt_secret", "JWT 密钥强度", "fail",
                "仍在使用开发默认密钥",
                "export JWT_SECRET=$(openssl rand -base64 32)");
    }

    private Map<String, Object> checkPayCallbackSecret() {
        if (payCallbackSecret != null && !payCallbackSecret.isBlank()
                && !"xiaowei-pay-dev-secret".equals(payCallbackSecret)) {
            return check("pay_callback_secret", "支付回调密钥", "ok", "已使用自定义密钥", null);
        }
        return check("pay_callback_secret", "支付回调密钥", "fail",
                "仍在使用开发默认 PAY_CALLBACK_SECRET",
                "export PAY_CALLBACK_SECRET=$(openssl rand -base64 24)");
    }

    private Map<String, Object> checkPaymentMock() {
        if (!paymentMockEnabled) {
            return check("payment_mock", "支付模拟开关", "ok",
                    "PAYMENT_MOCK_ENABLED=false", null);
        }
        return check("payment_mock", "支付模拟开关", "warn",
                "仍允许模拟支付（开发用）",
                "生产设置 PAYMENT_MOCK_ENABLED=false，并配置微信 Native");
    }

    private Map<String, Object> checkWechatPay() {
        if (!wechatPayProperties.isEnabled()) {
            return check("wechat_pay", "微信 Native 支付", "warn",
                    "未启用（可仅用余额/礼包码）",
                    "需要微信支付时设置 WECHAT_PAY_ENABLED=true 并补全商户参数");
        }
        if (wechatPayProperties.isConfigured()) {
            return check("wechat_pay", "微信 Native 支付", "ok",
                    "参数已齐全（含 apiV3Key、serialNo，可接 V3 异步通知）", null);
        }
        return check("wechat_pay", "微信 Native 支付", "fail",
                "已启用但缺少 appId/mchId/apiV3Key/serialNo/私钥/notifyUrl",
                "补全 WECHAT_* 环境变量，notifyUrl 指向 /api/payments/callback/wechat");
    }

    private Map<String, Object> checkAlipayPay() {
        if (!alipayPayProperties.isEnabled()) {
            return check("alipay_pay", "支付宝当面付", "warn",
                    "未启用（可仅用微信/余额/礼包码）",
                    "需要支付宝时设置 ALIPAY_PAY_ENABLED=true 并补全应用参数");
        }
        if (alipayPayProperties.isConfigured()) {
            return check("alipay_pay", "支付宝当面付", "ok",
                    "参数已齐全（可接异步通知）", null);
        }
        return check("alipay_pay", "支付宝当面付", "fail",
                "已启用但缺少 appId/私钥/支付宝公钥/notifyUrl",
                "补全 ALIPAY_* 环境变量，notifyUrl 指向 /api/payments/callback/alipay");
    }

    private Map<String, Object> checkWalletDirectRecharge() {
        if (!allowDirectRecharge) {
            return check("wallet_direct", "钱包直充接口", "ok", "已关闭（须走 prepay）", null);
        }
        return check("wallet_direct", "钱包直充接口", "warn",
                "允许 POST /wallet/recharge 直接加余额",
                "生产保持 allow-direct-recharge=false");
    }

    private Map<String, Object> checkReferralFrontendUrl() {
        if (referralFrontendBaseUrl != null && !referralFrontendBaseUrl.isBlank()) {
            String u = referralFrontendBaseUrl.trim();
            if (u.startsWith("http://") || u.startsWith("https://")) {
                return check("referral_frontend_url", "分享链接域名", "ok",
                        "REFERRAL_FRONTEND_BASE_URL=" + u, null);
            }
            return check("referral_frontend_url", "分享链接域名", "warn",
                    "REFERRAL_FRONTEND_BASE_URL 须以 http(s):// 开头: " + u,
                    "示例: https://your-domain.com/");
        }
        return check("referral_frontend_url", "分享链接域名", "warn",
                "未配置 REFERRAL_FRONTEND_BASE_URL，分享链接为相对路径",
                "export REFERRAL_FRONTEND_BASE_URL=https://你的域名/");
    }

    private Map<String, Object> checkCorsProd() {
        boolean prod = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p));
        if (!prod) {
            return check("cors_prod", "生产 CORS", "ok", "非 prod profile", null);
        }
        if (corsAllowedOrigins != null && !corsAllowedOrigins.isBlank()) {
            return check("cors_prod", "生产 CORS", "ok",
                    "已配置 CORS_ALLOWED_ORIGINS", null);
        }
        return check("cors_prod", "生产 CORS", "ok",
                "未配置（同域 Nginx 反代时正常）",
                "前后端分域时请设置 CORS_ALLOWED_ORIGINS");
    }

    private boolean isWeakJwt() {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            return true;
        }
        return DEFAULT_JWT_HINT.equals(jwtSecret);
    }

    @SuppressWarnings("unchecked")
    private int countEnabledModelsWithKey() {
        List<Map<String, Object>> pools = (List<Map<String, Object>>)
                aiConfigService.getConfigForAdmin().getOrDefault("modelPools", List.of());
        int n = 0;
        for (Map<String, Object> p : pools) {
            boolean enabled = !Boolean.FALSE.equals(p.get("enabled"));
            boolean keySet = Boolean.TRUE.equals(p.get("apiKeySet"));
            if (enabled && keySet) n++;
        }
        return n;
    }

    private Map<String, Object> check(String id, String label, String status, String message, String fix) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("label", label);
        m.put("status", status);
        m.put("message", message);
        if (fix != null) m.put("fix", fix);
        return m;
    }

    private Map<String, String> envTemplates() {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("server", """
                # xiaowei-server 生产环境（保存为 .env 或导出到 shell）
                SPRING_PROFILES_ACTIVE=prod,mysql
                MYSQL_HOST=localhost
                MYSQL_PORT=3306
                MYSQL_DB=xiaowei
                MYSQL_USER=root
                MYSQL_PASSWORD=your_password
                JWT_SECRET=请替换为至少32字符随机串
                PAY_CALLBACK_SECRET=请替换为支付回调密钥
                PAYMENT_MOCK_ENABLED=false
                REFERRAL_FRONTEND_BASE_URL=https://your-domain.com/
                # CORS_ALLOWED_ORIGINS=https://your-domain.com
                WECHAT_PAY_ENABLED=false
                # WECHAT_APP_ID=
                # WECHAT_MCH_ID=
                # WECHAT_API_V3_KEY=
                # WECHAT_SERIAL_NO=
                # WECHAT_PRIVATE_KEY_PEM=
                # WECHAT_NOTIFY_URL=https://your-domain/api/payments/callback/wechat
                ALIPAY_PAY_ENABLED=false
                # ALIPAY_APP_ID=
                # ALIPAY_PRIVATE_KEY_PEM=
                # ALIPAY_PUBLIC_KEY_PEM=
                ALIPAY_GATEWAY_URL=https://openapi.alipay.com/gateway.do
                # ALIPAY_NOTIFY_URL=https://your-domain/api/payments/callback/alipay
                # 沙箱联调见 xiaowei-server/.env.sandbox.example
                """);
        t.put("userFrontend", """
                # 用户端 .env.production
                VITE_USE_API=true
                VITE_API_BASE=/api
                """);
        t.put("adminFrontend", """
                # 管理端 xiaowei-admin/.env.production
                VITE_API_BASE=/api
                """);
        return t;
    }

    private String nginxSnippet() {
        return """
                location /api/ {
                  proxy_pass http://127.0.0.1:8080/api/;
                  proxy_set_header Host $host;
                  proxy_set_header X-Real-IP $remote_addr;
                  proxy_http_version 1.1;
                  proxy_set_header Connection "";
                  proxy_buffering off;
                  proxy_read_timeout 3600s;
                }
                location / {
                  root /var/www/xiaowei/dist;
                  try_files $uri $uri/ /index.html;
                }
                location /admin/ {
                  alias /var/www/xiaowei-admin/dist/;
                  try_files $uri $uri/ /admin/index.html;
                }
                """;
    }
}
