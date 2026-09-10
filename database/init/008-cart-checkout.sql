-- Run after 007 with application writers stopped. MySQL DDL commits implicitly.
SET @novamall_cart_version_ddl = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'mall_cart_item' AND column_name = 'version'),
    'SELECT 1', 'ALTER TABLE mall_cart_item ADD COLUMN version BIGINT NOT NULL DEFAULT 0');
PREPARE novamall_cart_version_stmt FROM @novamall_cart_version_ddl;
EXECUTE novamall_cart_version_stmt;
DEALLOCATE PREPARE novamall_cart_version_stmt;

CREATE TABLE IF NOT EXISTS mall_cart_checkout (
    user_id BIGINT NOT NULL,
    request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    address_id BIGINT NOT NULL,
    order_no VARCHAR(32) NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (user_id, request_key),
    UNIQUE KEY uk_cart_checkout_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mall_cart_cleanup (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    payload JSON NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_cart_cleanup_order (order_no),
    KEY idx_cart_cleanup_pending (next_attempt_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
