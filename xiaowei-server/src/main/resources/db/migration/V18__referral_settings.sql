CREATE TABLE referral_settings (
    id              BIGINT PRIMARY KEY,
    inviter_reward  DECIMAL(10,2) NOT NULL DEFAULT 5.00,
    invitee_reward  DECIMAL(10,2) NOT NULL DEFAULT 2.00,
    rules_text      TEXT,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO referral_settings (id, inviter_reward, invitee_reward, enabled)
VALUES (1, 5.00, 2.00, TRUE);
