CREATE TABLE site_branding_settings (
    id              BIGINT PRIMARY KEY,
    site_title      VARCHAR(128) NOT NULL DEFAULT '小微智能写作',
    slogan          VARCHAR(256) NOT NULL DEFAULT '一站式论文辅助平台',
    document_title  VARCHAR(128) NOT NULL DEFAULT '小微智能 AI 论文写作',
    logo_text       VARCHAR(16) NOT NULL DEFAULT 'AI',
    logo_url        VARCHAR(512),
    favicon_url     VARCHAR(512),
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO site_branding_settings (id, site_title, slogan, document_title, logo_text)
VALUES (1, '小微智能写作', '一站式论文辅助平台', '小微智能 AI 论文写作', 'AI');
