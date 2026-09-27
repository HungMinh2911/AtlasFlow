CREATE TABLE IF NOT EXISTS processed_messages (
    message_id UUID PRIMARY KEY,
    consumer_name VARCHAR(128) NOT NULL,
    aggregate_id UUID,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
