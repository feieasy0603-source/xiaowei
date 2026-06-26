ALTER TABLE papers ADD COLUMN version BIGINT NOT NULL DEFAULT 1;

UPDATE products
SET title_field_label = '上传论文或报告',
    submit_label = '提交检测说明'
WHERE id = 'aigc';
