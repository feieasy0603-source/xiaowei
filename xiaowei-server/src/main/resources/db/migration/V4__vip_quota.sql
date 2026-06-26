CREATE TABLE vip_quota_config (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    vip_level        INT NOT NULL,
    task_type        VARCHAR(32) NOT NULL,
    daily_free       INT NOT NULL DEFAULT 0,
    discount_percent INT NOT NULL DEFAULT 0,
    enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (vip_level, task_type)
);

CREATE TABLE user_daily_quota (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    usage_date DATE NOT NULL,
    task_type  VARCHAR(32) NOT NULL,
    used_count INT NOT NULL DEFAULT 0,
    UNIQUE (user_id, usage_date, task_type),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

INSERT INTO vip_quota_config (vip_level, task_type, daily_free, discount_percent) VALUES
(0, 'paper_generate', 0, 0),
(0, 'revise', 0, 0),
(0, 'paraphrase', 0, 0),
(0, 'ppt_generate', 0, 0),
(1, 'paper_generate', 1, 10),
(1, 'revise', 2, 15),
(1, 'paraphrase', 2, 10),
(2, 'paper_generate', 3, 20),
(2, 'revise', 5, 25),
(2, 'paraphrase', 5, 20),
(2, 'ppt_generate', 2, 15);
