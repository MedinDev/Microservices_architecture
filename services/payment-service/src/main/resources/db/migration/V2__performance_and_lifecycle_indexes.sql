CREATE INDEX IF NOT EXISTS idx_refund_records_created_at ON refund_records(created_at);
CREATE INDEX IF NOT EXISTS idx_payment_transaction_logs_created_at ON payment_transaction_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_payment_transaction_logs_payment_created_at ON payment_transaction_logs(payment_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_processed_payment_events_processed_at ON processed_payment_events(processed_at);
