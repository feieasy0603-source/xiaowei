-- 恢复默认可用的 Mock 生成配置（避免错误模型名导致仅输出「演示内容」）
UPDATE ai_runtime_config
SET config_json = '{"mock":true,"provider":"mock","baseUrl":"","modelName":"mock","apiKey":"","organization":"","apiVersion":"2024-02-15-preview","timeoutMs":120000,"maxConcurrency":4,"temperature":0.7,"taskTypeModels":{}}',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1;
