CREATE TABLE wallet_recharge_orders (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no    VARCHAR(64)  NOT NULL UNIQUE,
    user_id     BIGINT       NOT NULL,
    amount      DECIMAL(12, 2) NOT NULL,
    pay_status  VARCHAR(16)  NOT NULL DEFAULT 'pending',
    paid_at     TIMESTAMP    NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wallet_recharge_user (user_id, pay_status)
);
