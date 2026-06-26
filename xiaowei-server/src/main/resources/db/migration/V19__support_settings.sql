CREATE TABLE support_settings (
    id            BIGINT PRIMARY KEY,
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    title         VARCHAR(128) NOT NULL DEFAULT '在线客服',
    work_hours    VARCHAR(256) NOT NULL DEFAULT '工作日 9:00–18:00',
    phone         VARCHAR(32),
    email         VARCHAR(128),
    wechat_id     VARCHAR(64),
    qq            VARCHAR(32),
    external_url  VARCHAR(512),
    note          TEXT,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO support_settings (id, title, work_hours, phone, wechat_id, qq, note)
VALUES (
    1,
    '在线客服',
    '工作日 9:00–18:00',
    '400-000-0000',
    'xiaowei-service',
    '',
    '添加微信请备注「小微写作」；学校模板、开票与充值问题均可咨询。'
);
