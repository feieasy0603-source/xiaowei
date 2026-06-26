CREATE TABLE ai_runtime_config (
    id          BIGINT PRIMARY KEY,
    config_json TEXT NOT NULL,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO ai_runtime_config (id, config_json) VALUES (1, '{}');
