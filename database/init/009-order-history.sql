-- Requires 007-mall-model-upgrade.sql. Existing orders are not backfilled.
-- History starts when this feature is enabled; missing legacy records stay missing.
CREATE TABLE IF NOT EXISTS mall_order_history (
    id BIGINT NOT NULL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT NULL,
    note VARCHAR(255) NOT NULL,
    occurred_at DATETIME NOT NULL,
    UNIQUE KEY uk_order_history_transition (order_id, to_status),
    KEY idx_order_history_timeline (order_id, occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
