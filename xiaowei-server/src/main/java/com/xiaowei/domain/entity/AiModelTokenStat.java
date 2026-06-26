package com.xiaowei.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "ai_model_token_stats")
public class AiModelTokenStat {

    @Id
    @Column(name = "endpoint_id", length = 64)
    private String endpointId;

    @Column(nullable = false, length = 128)
    private String label = "";

    @Column(nullable = false, length = 32)
    private String provider = "";

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName = "";

    @Column(name = "prompt_tokens", nullable = false)
    private long promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private long completionTokens;

    @Column(name = "total_tokens", nullable = false)
    private long totalTokens;

    @Column(name = "request_count", nullable = false)
    private long requestCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
