-- 支付回调以 trade_no 幂等；数据库层防止并发回调重复入账
CREATE UNIQUE INDEX idx_payment_records_trade_no_unique ON payment_records(trade_no);
