package com.xiaowei.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 生产环境启动时校验关键密钥，避免带着默认配置上线。
 */
@Slf4j
@Component
public class ProductionStartupValidator implements ApplicationRunner {

    private static final String DEFAULT_JWT =
            "xiaowei-dev-secret-change-in-production-min-32-chars";
    private static final String DEFAULT_PAY_SECRET = "xiaowei-pay-dev-secret";

    private final Environment environment;

    @Value("${xiaowei.jwt.secret}")
    private String jwtSecret;

    @Value("${xiaowei.payment.callback-secret:xiaowei-pay-dev-secret}")
    private String payCallbackSecret;

    public ProductionStartupValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProd()) {
            return;
        }
        List<String> errors = new ArrayList<>();
        if (jwtSecret == null || jwtSecret.length() < 32 || DEFAULT_JWT.equals(jwtSecret)) {
            errors.add("JWT_SECRET 未设置或过弱（至少 32 字符且非开发默认值）");
        }
        if (payCallbackSecret == null || payCallbackSecret.isBlank()
                || DEFAULT_PAY_SECRET.equals(payCallbackSecret)) {
            errors.add("PAY_CALLBACK_SECRET 未设置或仍为开发默认值");
        }
        if (usesMysql() && isBlank(System.getenv("MYSQL_PASSWORD"))
                && isBlank(environment.getProperty("MYSQL_PASSWORD"))) {
            errors.add("MYSQL_PASSWORD 未设置（prod+mysql 环境）");
        }
        if (!errors.isEmpty()) {
            for (String e : errors) {
                log.error("生产启动校验失败: {}", e);
            }
            throw new IllegalStateException(
                    "生产环境配置不完整: " + String.join("; ", errors));
        }
        log.info("生产环境密钥校验通过");
    }

    private boolean isProd() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p));
    }

    private boolean usesMysql() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "mysql".equalsIgnoreCase(p));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
