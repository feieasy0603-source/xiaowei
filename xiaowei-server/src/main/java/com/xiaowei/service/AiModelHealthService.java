package com.xiaowei.service;

import com.xiaowei.integration.ai.LlmChatResult;
import com.xiaowei.integration.ai.LlmEndpoint;
import com.xiaowei.integration.ai.OpenAiCompatibleClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 模型池连通性与健康状态探测 */
@Service
@RequiredArgsConstructor
public class AiModelHealthService {

    private final AiConfigService aiConfigService;
    private final OpenAiCompatibleClient llmClient;

    public Map<String, Object> probeAll() {
        long start = System.currentTimeMillis();
        if (aiConfigService.isMock()) {
            Map<String, Object> m = new HashMap<>();
            m.put("ok", true);
            m.put("mode", "mock");
            m.put("message", "Mock 模式无需探测");
            m.put("results", List.of());
            m.put("successCount", 0);
            m.put("total", 0);
            m.put("latencyMs", System.currentTimeMillis() - start);
            return m;
        }
        List<LlmEndpoint> endpoints = aiConfigService.allEndpoints();
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        int probed = 0;
        for (LlmEndpoint ep : endpoints) {
            Map<String, Object> row = probeOne(ep);
            results.add(row);
            if (!"skip".equals(row.get("healthStatus"))) {
                probed++;
                if ("ok".equals(row.get("healthStatus"))) {
                    success++;
                }
            }
        }
        aiConfigService.applyEndpointHealth(results);
        Map<String, Object> m = new HashMap<>();
        m.put("ok", probed > 0 && success == probed);
        m.put("mode", "llm");
        m.put("results", results);
        m.put("successCount", success);
        m.put("total", probed);
        m.put("latencyMs", System.currentTimeMillis() - start);
        return m;
    }

    private Map<String, Object> probeOne(LlmEndpoint ep) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", ep.id());
        row.put("label", ep.label());
        row.put("provider", ep.provider());
        row.put("modelName", ep.modelName());
        if (!ep.enabled()) {
            row.put("healthStatus", "skip");
            row.put("healthMessage", "已禁用");
            row.put("ok", false);
            return row;
        }
        if (ep.apiKey() == null || ep.apiKey().isBlank()) {
            row.put("healthStatus", "fail");
            row.put("healthMessage", "未配置 API Key");
            row.put("ok", false);
            return row;
        }
        long t0 = System.currentTimeMillis();
        try {
            String model = aiConfigService.modelForEndpoint("connectivity_test", ep);
            LlmChatResult chat = aiConfigService.runOnEndpoint(ep, () -> llmClient.chatOnEndpoint(
                    ep,
                    model,
                    "你是连通性测试助手。",
                    "请只回复：OK"
            ));
            row.put("healthStatus", "ok");
            row.put("healthMessage", chat.content() != null && !chat.content().isBlank()
                    ? chat.content().trim()
                    : "可用");
            row.put("ok", true);
            row.put("latencyMs", System.currentTimeMillis() - t0);
            row.put("reply", chat.content());
            row.put("totalTokens", chat.totalTokens());
        } catch (Exception e) {
            row.put("healthStatus", "fail");
            row.put("healthMessage", shorten(e.getMessage()));
            row.put("ok", false);
            row.put("latencyMs", System.currentTimeMillis() - t0);
        }
        return row;
    }

    private static String shorten(String msg) {
        if (msg == null || msg.isBlank()) {
            return "连接失败";
        }
        return msg.length() > 200 ? msg.substring(0, 200) + "…" : msg;
    }
}
