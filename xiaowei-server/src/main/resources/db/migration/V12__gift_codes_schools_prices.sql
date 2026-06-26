CREATE TABLE gift_codes (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    code        VARCHAR(64) NOT NULL UNIQUE,
    amount      DECIMAL(10,2) NOT NULL,
    used_by     BIGINT,
    used_at     TIMESTAMP,
    expires_at  TIMESTAMP,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE schools (
    id          VARCHAR(32) PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO schools (id, name, sort_order) VALUES
('pku', '北京大学', 1),
('thu', '清华大学', 2),
('fudan', '复旦大学', 3),
('sjtu', '上海交通大学', 4),
('zju', '浙江大学', 5),
('nju', '南京大学', 6),
('whu', '武汉大学', 7),
('sysu', '中山大学', 8),
('other', '其他高校（联系客服添加）', 99);

INSERT INTO product_prices (product_id, degree, word_count, model_type, price) VALUES
('revise', NULL, NULL, 'standard', 9.90),
('paraphrase', NULL, NULL, 'standard', 12.90),
('paraphrase', NULL, NULL, 'academia', 19.90),
('aigc', NULL, NULL, 'standard', 8.90),
('ppt', NULL, NULL, 'standard', 15.90),
('translate', NULL, NULL, 'standard', 11.90),
('data', NULL, NULL, 'standard', 16.90),
('course', '本科', 8000, 'standard', 24.90),
('review', '本科', 10000, 'standard', 27.90),
('proposal', '本科', 6000, 'standard', 22.90),
('task', '本科', 5000, 'standard', 18.90),
('intern', '本科', 6000, 'standard', 21.90),
('survey', '本科', 8000, 'standard', 23.90),
('book', NULL, 3000, 'standard', 14.90),
('ideology', NULL, 4000, 'standard', 16.90),
('imitate', '本科', 10000, 'standard', 26.90),
('report', '本科', 8000, 'standard', 24.90);
