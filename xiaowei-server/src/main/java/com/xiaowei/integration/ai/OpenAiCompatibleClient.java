package com.xiaowei.integration.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaowei.common.BusinessException;
import com.xiaowei.service.AiConfigService;
import com.xiaowei.service.AiModelUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiCompatibleClient {

    private final AiConfigService aiConfigService;
    private final AiModelUsageService aiModelUsageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String chat(String taskType, String systemPrompt, String userPrompt) {
        return chatCompletion(taskType, systemPrompt, userPrompt, false, 4096);
    }

    public String chatWithModel(String model, String systemPrompt, String userPrompt) {
        return chatWithModel(model, systemPrompt, userPrompt, false, 4096);
    }

    public String chatCompletion(
            String taskType,
            String systemPrompt,
            String userPrompt,
            boolean jsonObjectMode,
            int maxTokens
    ) {
        return chatCompletionDetailed(taskType, systemPrompt, userPrompt, jsonObjectMode, maxTokens).content();
    }

    public LlmChatResult chatCompletionDetailed(
            String taskType,
            String systemPrompt,
            String userPrompt,
            boolean jsonObjectMode,
            int maxTokens
    ) {
        String model = aiConfigService.modelForTaskType(taskType);
        return chatWithModelDetailed(model, systemPrompt, userPrompt, jsonObjectMode, maxTokens);
    }

    /** 在指定接入点上调用（用于批量连通测试） */
    public LlmChatResult chatOnEndpoint(
            LlmEndpoint endpoint,
            String model,
            String systemPrompt,
            String userPrompt
    ) {
        return invoke(endpoint, model, systemPrompt, userPrompt, false, 256);
    }

    private String chatWithModel(String model, String systemPrompt, String userPrompt, boolean jsonObjectMode, int maxTokens) {
        return chatWithModelDetailed(model, systemPrompt, userPrompt, jsonObjectMode, maxTokens).content();
    }

    private LlmChatResult chatWithModelDetailed(
            String model, String systemPrompt, String userPrompt, boolean jsonObjectMode, int maxTokens) {
        LlmEndpoint ep = aiConfigService.requireEndpoint();
        return invoke(ep, model, systemPrompt, userPrompt, jsonObjectMode, maxTokens);
    }

    private LlmChatResult invoke(
            LlmEndpoint ep,
            String model,
            String systemPrompt,
            String userPrompt,
            boolean jsonObjectMode,
            int maxTokens
    ) {
        String provider = ep.provider();
        String apiKey = ep.apiKey();
        if (apiKey.isBlank()) {
            throw new BusinessException("未配置 API Key：" + ep.label());
        }

        double temperature = aiConfigService.temperature();
        String baseUrl = normalizeBaseUrl(ep.baseUrl(), provider);

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        );

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        if (jsonObjectMode && !AiProviderPreset.AZURE.equals(provider)) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        try {
            String raw;
            if (AiProviderPreset.AZURE.equals(provider)) {
                builder.defaultHeader("api-key", apiKey);
                RestClient client = builder.build();
                String apiVersion = ep.apiVersion();
                body.put("model", model);
                raw = client.post()
                        .uri(uriBuilder -> uriBuilder
                                .path("/openai/deployments/{deployment}/chat/completions")
                                .queryParam("api-version", apiVersion)
                                .build(model))
                        .body(body)
                        .retrieve()
                        .body(String.class);
            } else {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
                String org = ep.organization();
                if (!org.isBlank() && !"null".equals(org)) {
                    builder.defaultHeader("OpenAI-Organization", org);
                }
                RestClient client = builder.build();
                raw = client.post()
                        .uri("/chat/completions")
                        .body(body)
                        .retrieve()
                        .body(String.class);
            }
            LlmChatResult result = extractContentResult(raw);
            aiModelUsageService.record(ep, model, result.promptTokens(), result.completionTokens(), result.totalTokens());
            return result;
        } catch (RestClientResponseException e) {
            String detail = e.getResponseBodyAsString();
            if (detail != null && detail.length() > 300) {
                detail = detail.substring(0, 300) + "...";
            }
            throw new BusinessException("AI 调用失败 (" + e.getStatusCode().value() + "): " + detail);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("AI 调用异常: " + e.getMessage());
        }
    }

    private LlmChatResult extractContentResult(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("AI 返回为空");
        }
        JsonNode root = objectMapper.readTree(raw);
        JsonNode choice = root.path("choices").path(0);
        JsonNode content = choice.path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            JsonNode err = root.path("error").path("message");
            if (!err.isMissingNode()) {
                throw new BusinessException(err.asText());
            }
            throw new BusinessException("AI 响应格式异常");
        }
        String finish = choice.path("finish_reason").asText("");
        JsonNode usage = root.path("usage");
        long prompt = usage.path("prompt_tokens").asLong(0);
        long completion = usage.path("completion_tokens").asLong(0);
        long total = usage.path("total_tokens").asLong(prompt + completion);
        return new LlmChatResult(content.asText().trim(), finish, prompt, completion, total);
    }

    private String normalizeBaseUrl(String baseUrl, String provider) {
        if (baseUrl == null || baseUrl.isBlank()) {
            AiProviderPreset.Preset preset = AiProviderPreset.find(provider);
            return trimSlash(preset.defaultBaseUrl());
        }
        return trimSlash(baseUrl.trim());
    }

    private String trimSlash(String u) {
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}
