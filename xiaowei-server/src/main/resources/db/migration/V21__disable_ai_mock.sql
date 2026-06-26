-- 彻底关闭历史库中的 AI Mock 配置；真实 API Key 仍需在管理端模型池配置
UPDATE ai_runtime_config
SET config_json = '{"mock":false,"provider":"openai","baseUrl":"https://api.openai.com/v1","modelName":"gpt-4o-mini","apiKey":"","organization":"","apiVersion":"2024-02-15-preview","timeoutMs":120000,"maxConcurrency":4,"temperature":0.7,"taskTypeModels":{},"modelPools":[],"poolStrategy":"round_robin","globalConcurrencyMode":"auto","slotAcquireTimeoutMs":90000,"taskTypeEndpoints":{}}',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1
  AND (config_json LIKE '%"provider":"mock"%'
       OR config_json LIKE '%"provider": "mock"%'
       OR config_json LIKE '%"mock":true%'
       OR config_json LIKE '%"mock": true%');
