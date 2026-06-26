ALTER TABLE users ADD COLUMN referral_code VARCHAR(16) NULL;
ALTER TABLE users ADD COLUMN invited_by_user_id BIGINT NULL;

CREATE UNIQUE INDEX idx_users_referral_code ON users(referral_code);
