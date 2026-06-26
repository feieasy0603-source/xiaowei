-- 普通用户每日 1 次免费预览生成，避免 VIP0 完全无法调用 AI
UPDATE vip_quota_config
SET daily_free = 1
WHERE vip_level = 0 AND task_type = 'paper_generate' AND daily_free = 0;
