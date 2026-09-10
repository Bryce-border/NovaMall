CREATE TABLE IF NOT EXISTS mall_inventory (
    id BIGINT NOT NULL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sku_id BIGINT NULL,
    total_stock INT NOT NULL,
    available_stock INT NOT NULL,
    locked_stock INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    legacy_product_id BIGINT GENERATED ALWAYS AS (IF(sku_id IS NULL, product_id, NULL)) STORED,
    UNIQUE KEY uk_mall_inventory_legacy (legacy_product_id),
    KEY idx_inventory_product (product_id),
    UNIQUE KEY uk_mall_inventory_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mall_inventory_reservation (
    id BIGINT NOT NULL PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    product_id BIGINT NOT NULL,
    sku_id BIGINT NULL,
    quantity INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    legacy_product_id BIGINT GENERATED ALWAYS AS (IF(sku_id IS NULL, product_id, NULL)) STORED,
    UNIQUE KEY uk_mall_reservation_legacy (order_no, legacy_product_id),
    KEY idx_reservation_product (product_id),
    UNIQUE KEY uk_mall_reservation_order_sku (order_no, sku_id),
    KEY idx_mall_reservation_order_status (order_no, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
