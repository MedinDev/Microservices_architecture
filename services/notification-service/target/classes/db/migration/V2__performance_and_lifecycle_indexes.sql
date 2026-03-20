CREATE INDEX IF NOT EXISTS idx_notifications_user_read_created_at ON notifications(user_id, read_flag, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_read_created_at ON notifications(read_flag, created_at);
CREATE INDEX IF NOT EXISTS idx_processed_notification_events_processed_at ON processed_notification_events(processed_at);
