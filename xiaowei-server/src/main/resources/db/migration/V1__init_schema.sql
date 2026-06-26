-- 用户
CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone         VARCHAR(20) UNIQUE,
    password_hash VARCHAR(255),
    wx_open_id    VARCHAR(64) UNIQUE,
    nickname      VARCHAR(64),
    balance       DECIMAL(12,2) NOT NULL DEFAULT 0,
    vip_level     INT NOT NULL DEFAULT 0,
    status        VARCHAR(16) NOT NULL DEFAULT 'active',
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 渠道 dCode
CREATE TABLE channels (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    d_code          VARCHAR(32) NOT NULL UNIQUE,
    name            VARCHAR(64) NOT NULL,
    commission_rate DECIMAL(5,4) NOT NULL DEFAULT 0,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 产品
CREATE TABLE products (
    id                 VARCHAR(32) PRIMARY KEY,
    label              VARCHAR(64) NOT NULL,
    icon               VARCHAR(16),
    badge              VARCHAR(16),
    banner             VARCHAR(512),
    process_variant    VARCHAR(32) NOT NULL,
    form_variant       VARCHAR(32) NOT NULL,
    task_type          VARCHAR(32) NOT NULL,
    flow_type          VARCHAR(16) NOT NULL DEFAULT 'both',
    title_field_label  VARCHAR(128),
    title_placeholder  VARCHAR(512),
    pro_link_text      VARCHAR(256),
    submit_label       VARCHAR(64),
    agreement_text     TEXT,
    show_faq           BOOLEAN NOT NULL DEFAULT TRUE,
    center_title       BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order         INT NOT NULL DEFAULT 0,
    enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    config_json        TEXT,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE product_prices (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  VARCHAR(32) NOT NULL,
    degree      VARCHAR(16),
    word_count  INT,
    model_type  VARCHAR(16),
    price       DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (product_id) REFERENCES products(id)
);

-- 论文草稿
CREATE TABLE papers (
    id              VARCHAR(32) PRIMARY KEY,
    user_id         BIGINT,
    product_id      VARCHAR(32),
    title           VARCHAR(256),
    draft_json      TEXT NOT NULL,
    max_visited_step INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE paper_files (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    paper_id    VARCHAR(32) NOT NULL,
    job_id      BIGINT,
    file_type   VARCHAR(32) NOT NULL,
    file_name   VARCHAR(256) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    size_bytes  BIGINT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (paper_id) REFERENCES papers(id)
);

-- 订单
CREATE TABLE orders (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no        VARCHAR(32) NOT NULL UNIQUE,
    user_id         BIGINT NOT NULL,
    product_id      VARCHAR(32) NOT NULL,
    paper_id        VARCHAR(32),
    channel_id      BIGINT,
    amount          DECIMAL(10,2) NOT NULL,
    pay_status      VARCHAR(16) NOT NULL DEFAULT 'unpaid',
    pay_method      VARCHAR(16),
    paid_at         TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (product_id) REFERENCES products(id),
    FOREIGN KEY (channel_id) REFERENCES channels(id)
);

-- 异步任务
CREATE TABLE jobs (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_no       VARCHAR(32) NOT NULL UNIQUE,
    user_id      BIGINT,
    product_id   VARCHAR(32) NOT NULL,
    paper_id     VARCHAR(32),
    order_id     BIGINT,
    task_type    VARCHAR(32) NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'pending',
    progress     INT NOT NULL DEFAULT 0,
    payload_json TEXT,
    result_json  TEXT,
    error_msg    TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at  TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
);

-- 钱包流水
CREATE TABLE wallet_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    type        VARCHAR(16) NOT NULL,
    amount      DECIMAL(12,2) NOT NULL,
    balance_after DECIMAL(12,2) NOT NULL,
    ref_type    VARCHAR(32),
    ref_id      VARCHAR(64),
    remark      VARCHAR(256),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE payment_records (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    order_id    BIGINT,
    amount      DECIMAL(10,2) NOT NULL,
    pay_method  VARCHAR(16) NOT NULL,
    trade_no    VARCHAR(64),
    status      VARCHAR(16) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (order_id) REFERENCES orders(id)
);

-- 文献与提纲模板
CREATE TABLE literature_refs (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    title        VARCHAR(512) NOT NULL,
    authors      VARCHAR(256),
    source       VARCHAR(256),
    pub_year     INT,
    lang         VARCHAR(8) NOT NULL,
    gbt_citation TEXT NOT NULL,
    keywords     VARCHAR(256),
    enabled      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE outline_templates (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    title        VARCHAR(256) NOT NULL,
    category     VARCHAR(64),
    degree       VARCHAR(16),
    depth        INT NOT NULL DEFAULT 2,
    outline_json TEXT NOT NULL,
    enabled      BOOLEAN NOT NULL DEFAULT TRUE
);

-- 管理端
CREATE TABLE admin_users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname      VARCHAR(64),
    role          VARCHAR(32) NOT NULL DEFAULT 'admin',
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_jobs_user ON jobs(user_id);
CREATE INDEX idx_jobs_status ON jobs(status);
CREATE INDEX idx_papers_user ON papers(user_id);
