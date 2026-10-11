-- Phase 1: paid, unshipped merchandise refunds. Original order/payment snapshots stay intact.
CREATE TABLE IF NOT EXISTS mall_refund_allocation (
    order_item_id BIGINT NOT NULL PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    net_amount DECIMAL(12,2) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_refund_allocation_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mall_refund (
    refund_no VARCHAR(32) NOT NULL PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(32) NOT NULL,
    payment_no VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    review_note VARCHAR(500) NULL,
    reviewer_id BIGINT NULL,
    reviewed_at DATETIME(3) NULL,
    channel_refund_no VARCHAR(64) NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NULL,
    lease_until DATETIME(3) NULL,
    lease_token VARCHAR(36) NULL,
    last_error VARCHAR(500) NULL,
    succeeded_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_refund_request (user_id, order_no, request_key),
    KEY idx_refund_order (order_no, status),
    KEY idx_refund_user (user_id, created_at, refund_no),
    KEY idx_refund_status (status, created_at, refund_no),
    KEY idx_refund_created (created_at, refund_no),
    KEY idx_refund_buyer (user_name, created_at, refund_no),
    KEY idx_refund_jobs (status, next_attempt_at, lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mall_refund_item (
    refund_no VARCHAR(32) NOT NULL,
    order_item_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (refund_no, order_item_id),
    KEY idx_refund_item_order_item (order_item_id),
    CONSTRAINT fk_refund_item_refund FOREIGN KEY (refund_no) REFERENCES mall_refund(refund_no),
    CONSTRAINT fk_refund_item_order_item FOREIGN KEY (order_item_id) REFERENCES mall_order_item(id),
    CONSTRAINT chk_refund_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_refund_item_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mall_refund_history (
    id BIGINT NOT NULL PRIMARY KEY,
    refund_no VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_id BIGINT NULL,
    note VARCHAR(500) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    KEY idx_refund_history (refund_no, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mall_refund_attempt (
    id BIGINT NOT NULL PRIMARY KEY,
    refund_no VARCHAR(32) NOT NULL,
    lease_token VARCHAR(36) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    note VARCHAR(500) NULL,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NULL,
    UNIQUE KEY uk_refund_attempt_lease (lease_token),
    KEY idx_refund_attempt (refund_no, started_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A durable simulated payment-provider receipt, independent of order transactions.
CREATE TABLE IF NOT EXISTS mall_mock_refund_receipt (
    refund_no VARCHAR(32) NOT NULL PRIMARY KEY,
    payment_no VARCHAR(64) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    channel_refund_no VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Inventory consumer's business deduplication and partial-restock ledger.
CREATE TABLE IF NOT EXISTS mall_inventory_refund (
    refund_no VARCHAR(32) NOT NULL,
    reservation_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (refund_no, reservation_id),
    KEY idx_inventory_refund_reservation (reservation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
