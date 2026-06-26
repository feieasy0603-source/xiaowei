CREATE TABLE ai_model_token_stats (
    endpoint_id        VARCHAR(64)  PRIMARY KEY,
    label              VARCHAR(128) NOT NULL DEFAULT '',
    provider           VARCHAR(32)  NOT NULL DEFAULT '',
    model_name         VARCHAR(128) NOT NULL DEFAULT '',
    prompt_tokens      BIGINT       NOT NULL DEFAULT 0,
    completion_tokens  BIGINT       NOT NULL DEFAULT 0,
    total_tokens       BIGINT       NOT NULL DEFAULT 0,
    request_count      BIGINT       NOT NULL DEFAULT 0,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_model_token_stats_total ON ai_model_token_stats (total_tokens DESC);
