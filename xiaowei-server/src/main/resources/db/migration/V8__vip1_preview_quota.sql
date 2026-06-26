-- VIP1 每日预览次数过少会导致「一直无法生成」；适度提高免费预览额度
UPDATE vip_quota_config
SET daily_free = 5
WHERE vip_level = 1 AND task_type = 'paper_generate' AND daily_free < 5;
