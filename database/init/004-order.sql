CREATE TABLE IF NOT EXISTS mall_order (
    id BIGINT NOT NULL PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(32) NOT NULL,
    product_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    freight_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    remark VARCHAR(255) NULL,
    cancel_reason VARCHAR(255) NULL,
    paid_at DATETIME NULL,
    shipped_at DATETIME NULL,
    completed_at DATETIME NULL,
    cancelled_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_mall_order_no (order_no),
    KEY idx_mall_order_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mall_order_item (
    id BIGINT NOT NULL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    product_id BIGINT NOT NULL,
    sku_id BIGINT NULL,
    product_name VARCHAR(128) NOT NULL,
    cover_image_url VARCHAR(512) NULL,
    sku_name VARCHAR(128) NULL,
    spec_json JSON NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    KEY idx_mall_order_item_order (order_no),
    KEY idx_mall_order_item_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mall_order_address (
    id BIGINT NOT NULL PRIMARY KEY, order_id BIGINT NOT NULL,
    receiver_name VARCHAR(64) NOT NULL, receiver_phone VARCHAR(32) NOT NULL,
    province VARCHAR(64) NOT NULL, city VARCHAR(64) NOT NULL, district VARCHAR(64) NOT NULL,
    detail_address VARCHAR(255) NOT NULL, postal_code VARCHAR(16) NULL,
    UNIQUE KEY uk_order_address_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mall_payment (
    id BIGINT NOT NULL PRIMARY KEY, order_id BIGINT NOT NULL, order_no VARCHAR(32) NOT NULL,
    payment_no VARCHAR(64) NULL, channel VARCHAR(32) NOT NULL, amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(32) NOT NULL, paid_at DATETIME NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
    KEY idx_payment_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mall_shipment (
    id BIGINT NOT NULL PRIMARY KEY, order_id BIGINT NOT NULL, order_no VARCHAR(32) NOT NULL,
    company VARCHAR(64) NULL, tracking_no VARCHAR(64) NULL, status VARCHAR(32) NOT NULL,
    shipped_at DATETIME NULL, delivered_at DATETIME NULL, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
    KEY idx_shipment_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mall_cart_item (
    id BIGINT NOT NULL PRIMARY KEY, user_id BIGINT NOT NULL, product_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
    quantity INT NOT NULL, selected TINYINT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_cart_user_sku (user_id, sku_id), KEY idx_cart_user_selected (user_id, selected, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
