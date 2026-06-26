package com.xiaowei.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 启动时校验当前数据库来源，避免误用 H2 导致业务数据不可见。
 */
@Slf4j
@Component
public class DataSourceStartupLogger implements BeanFactoryPostProcessor, ApplicationRunner {

    private final Environment environment;

    public DataSourceStartupLogger(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        rejectH2IfPresent();
    }

    @Override
    public void run(ApplicationArguments args) {
        rejectH2IfPresent();
        String url = environment.getProperty("spring.datasource.url", "");
        String profiles = String.join(", ", environment.getActiveProfiles());
        if (profiles.isBlank()) {
            profiles = "(default)";
        }

        if (url.startsWith("jdbc:h2:mem:")) {
            throw rejectH2(profiles, url);
        }

        if (url.startsWith("jdbc:h2:file:")) {
            throw rejectH2(profiles, url);
        }

        if (url.startsWith("jdbc:mysql:")) {
            log.info("当前使用 MySQL 数据库。activeProfiles={}, datasource={}",
                    profiles, maskJdbcUrl(url));
            return;
        }

        log.info("当前数据库配置: activeProfiles={}, datasource={}", profiles, maskJdbcUrl(url));
    }

    private void rejectH2IfPresent() {
        String url = environment.getProperty("spring.datasource.url", "");
        String profiles = String.join(", ", environment.getActiveProfiles());
        if (profiles.isBlank()) {
            profiles = "(default)";
        }
        if (url.startsWith("jdbc:h2:mem:") || url.startsWith("jdbc:h2:file:")) {
            throw rejectH2(profiles, url);
        }
    }

    private static IllegalStateException rejectH2(String profiles, String url) {
        return new IllegalStateException(
                "H2 dev 数据源已禁用，只允许使用 MySQL。activeProfiles="
                        + profiles + ", datasource=" + maskJdbcUrl(url));
    }

    private static String maskJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            return "(empty)";
        }
        int queryStart = url.indexOf('?');
        if (queryStart < 0) {
            return url;
        }
        String base = url.substring(0, queryStart);
        String query = url.substring(queryStart + 1);
        String maskedQuery = Arrays.stream(query.split("&"))
                .map(part -> {
                    String lower = part.toLowerCase();
                    if (lower.startsWith("password=") || lower.startsWith("apikey=") || lower.startsWith("key=")) {
                        int eq = part.indexOf('=');
                        return eq >= 0 ? part.substring(0, eq + 1) + "***" : "***";
                    }
                    return part;
                })
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
        return maskedQuery.isBlank() ? base : base + "?" + maskedQuery;
    }
}
