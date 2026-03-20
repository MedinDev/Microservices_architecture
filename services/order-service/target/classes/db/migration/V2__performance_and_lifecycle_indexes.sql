CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_outbox_published_published_at ON order_outbox(published, published_at);
CREATE INDEX IF NOT EXISTS idx_processed_order_events_processed_at ON processed_order_events(processed_at);
