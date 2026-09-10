CREATE TABLE IF NOT EXISTS mall_category (
    id BIGINT NOT NULL PRIMARY KEY,
    parent_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_mall_category_parent (parent_id),
    KEY idx_mall_category_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mall_product (
    id BIGINT NOT NULL PRIMARY KEY,
    category_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    subtitle VARCHAR(255) NULL,
    description TEXT NULL,
    cover_image_url VARCHAR(512) NULL,
    price DECIMAL(12,2) NOT NULL,
    market_price DECIMAL(12,2) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_mall_product_category (category_id),
    KEY idx_mall_product_status (status),
    KEY idx_mall_product_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mall_product_sku (
    id BIGINT NOT NULL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sku_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    spec_json JSON NULL,
    image_url VARCHAR(512) NULL,
    price DECIMAL(12,2) NOT NULL,
    market_price DECIMAL(12,2) NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_product_sku_code (sku_code), KEY idx_product_sku_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mall_product_image (
    id BIGINT NOT NULL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    image_url VARCHAR(512) NOT NULL,
    image_type VARCHAR(16) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    KEY idx_product_image_product (product_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
