package com.xiaowei.service;

import com.xiaowei.domain.entity.AiModelTokenStat;
import com.xiaowei.domain.repository.AiModelTokenStatRepository;
import com.xiaowei.integration.ai.LlmEndpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiModelUsageService {

    private final AiModelTokenStatRepository repository;

    @Transactional
    public void record(LlmEndpoint endpoint, String modelName, long promptTokens, long completionTokens, long totalTokens) {
        if (endpoint == null || totalTokens <= 0 && promptTokens <= 0 && completionTokens <= 0) {
            return;
        }
        long prompt = Math.max(0, promptTokens);
        long completion = Math.max(0, completionTokens);
        long total = totalTokens > 0 ? totalTokens : prompt + completion;
        if (total <= 0) {
            return;
        }
        AiModelTokenStat row = repository.findById(endpoint.id()).orElseGet(() -> {
            AiModelTokenStat s = new AiModelTokenStat();
            s.setEndpointId(endpoint.id());
            return s;
        });
        row.setLabel(endpoint.label());
        row.setProvider(endpoint.provider());
        row.setModelName(modelName != null && !modelName.isBlank() ? modelName : endpoint.modelName());
        row.setPromptTokens(row.getPromptTokens() + prompt);
        row.setCompletionTokens(row.getCompletionTokens() + completion);
        row.setTotalTokens(row.getTotalTokens() + total);
        row.setRequestCount(row.getRequestCount() + 1);
        row.setUpdatedAt(Instant.now());
        repository.save(row);
    }

    public List<Map<String, Object>> listForAdmin() {
        return repository.findAllByOrderByTotalTokensDesc().stream().map(this::toMap).toList();
    }

    public Map<String, Object> summary() {
        Object[] agg = normalizeAggregateRow(repository.aggregateTotals());
        long prompt = longAt(agg, 0);
        long completion = longAt(agg, 1);
        long total = longAt(agg, 2);
        long requests = longAt(agg, 3);
        long modelCount = longAt(agg, 4);

        List<Map<String, Object>> topModels = repository.findAllByOrderByTotalTokensDesc().stream()
                .limit(20)
                .map(this::toMap)
                .toList();

        Map<String, Object> m = new HashMap<>();
        m.put("modelCount", modelCount);
        m.put("promptTokens", prompt);
        m.put("completionTokens", completion);
        m.put("totalTokens", total);
        m.put("requestCount", requests);
        m.put("models", topModels);
        return m;
    }

    private Object[] normalizeAggregateRow(Object[] raw) {
        if (raw == null || raw.length == 0) {
            return new Object[0];
        }
        if (raw.length == 1 && raw[0] instanceof Object[] nested) {
            return nested;
        }
        return raw;
    }

    private long longAt(Object[] row, int index) {
        if (row == null || index >= row.length || row[index] == null) {
            return 0;
        }
        return ((Number) row[index]).longValue();
    }

    private Map<String, Object> toMap(AiModelTokenStat s) {
        Map<String, Object> m = new HashMap<>();
        m.put("endpointId", s.getEndpointId());
        m.put("label", s.getLabel());
        m.put("provider", s.getProvider());
        m.put("modelName", s.getModelName());
        m.put("promptTokens", s.getPromptTokens());
        m.put("completionTokens", s.getCompletionTokens());
        m.put("totalTokens", s.getTotalTokens());
        m.put("requestCount", s.getRequestCount());
        m.put("updatedAt", s.getUpdatedAt());
        return m;
    }
}
