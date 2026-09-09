CREATE TABLE IF NOT EXISTS mall_order_outbox (
    id BIGINT NOT NULL PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'NEW',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    published_at DATETIME NULL,
    UNIQUE KEY uk_mall_order_outbox_message (message_id),
    KEY idx_mall_order_outbox_pending (status, next_attempt_at, created_at),
    KEY idx_mall_order_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
