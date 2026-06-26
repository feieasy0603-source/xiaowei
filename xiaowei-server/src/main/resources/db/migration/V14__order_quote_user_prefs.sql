-- 订单报价参数（支付前可重新计价）
ALTER TABLE orders ADD COLUMN quote_degree VARCHAR(32) NULL;
ALTER TABLE orders ADD COLUMN quote_word_count INT NULL;
ALTER TABLE orders ADD COLUMN quote_model_type VARCHAR(32) NULL;

-- 用户默认写作偏好（跨草稿复用）
ALTER TABLE users ADD COLUMN preferences_json TEXT NULL;
