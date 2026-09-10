-- MySQL 8.4. Stop application writers and back up the database before upgrading.
-- Safe to rerun: schema changes are checked against information_schema.
-- Existing order snapshots are never reconstructed from current product data.
CREATE TABLE IF NOT EXISTS mall_user (
    id BIGINT NOT NULL PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    avatar_url VARCHAR(512) NULL,
    phone VARCHAR(32) NULL,
    email VARCHAR(128) NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'USER',
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_mall_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mall_user_address (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    receiver_name VARCHAR(64) NOT NULL,
    receiver_phone VARCHAR(32) NOT NULL,
    province VARCHAR(64) NOT NULL,
    city VARCHAR(64) NOT NULL,
    district VARCHAR(64) NOT NULL,
    detail_address VARCHAR(255) NOT NULL,
    postal_code VARCHAR(16) NULL,
    is_default TINYINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_mall_user_address_user (user_id, deleted, is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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


DROP PROCEDURE IF EXISTS novamall_add_column;
DROP PROCEDURE IF EXISTS novamall_index;
DELIMITER $$
CREATE PROCEDURE novamall_add_column(IN t VARCHAR(64), IN c VARCHAR(64), IN definition TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = t AND column_name = c) THEN
        SET @ddl = CONCAT('ALTER TABLE `', t, '` ADD COLUMN `', c, '` ', definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
CREATE PROCEDURE novamall_index(IN t VARCHAR(64), IN n VARCHAR(64), IN definition TEXT)
BEGIN
    IF definition IS NULL THEN
        IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
                   AND table_name = t AND index_name = n) THEN
            SET @ddl = CONCAT('ALTER TABLE `', t, '` DROP INDEX `', n, '`');
            PREPARE stmt FROM @ddl;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    ELSEIF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE()
                       AND table_name = t AND index_name = n) THEN
        SET @ddl = CONCAT('ALTER TABLE `', t, '` ADD ', definition);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;
CALL novamall_add_column('mall_user', 'avatar_url', 'VARCHAR(512) NULL');
CALL novamall_add_column('mall_user', 'phone', 'VARCHAR(32) NULL');
CALL novamall_add_column('mall_user', 'email', 'VARCHAR(128) NULL');
CALL novamall_add_column('mall_product', 'description', 'TEXT NULL');
CALL novamall_add_column('mall_product', 'cover_image_url', 'VARCHAR(512) NULL');
CALL novamall_add_column('mall_product', 'market_price', 'DECIMAL(12,2) NULL');
CALL novamall_add_column('mall_order', 'product_amount', 'DECIMAL(12,2) NOT NULL DEFAULT 0');
CALL novamall_add_column('mall_order', 'discount_amount', 'DECIMAL(12,2) NOT NULL DEFAULT 0');
CALL novamall_add_column('mall_order', 'freight_amount', 'DECIMAL(12,2) NOT NULL DEFAULT 0');
CALL novamall_add_column('mall_order', 'remark', 'VARCHAR(255) NULL');
CALL novamall_add_column('mall_order', 'cancel_reason', 'VARCHAR(255) NULL');
CALL novamall_add_column('mall_order', 'paid_at', 'DATETIME NULL');
CALL novamall_add_column('mall_order', 'shipped_at', 'DATETIME NULL');
CALL novamall_add_column('mall_order', 'completed_at', 'DATETIME NULL');
CALL novamall_add_column('mall_order', 'cancelled_at', 'DATETIME NULL');
CALL novamall_add_column('mall_order_item', 'sku_id', 'BIGINT NULL');
CALL novamall_add_column('mall_order_item', 'cover_image_url', 'VARCHAR(512) NULL');
CALL novamall_add_column('mall_order_item', 'sku_name', 'VARCHAR(128) NULL');
CALL novamall_add_column('mall_order_item', 'spec_json', 'JSON NULL');
CALL novamall_add_column('mall_inventory', 'sku_id', 'BIGINT NULL');
CALL novamall_add_column('mall_inventory_reservation', 'sku_id', 'BIGINT NULL');
CALL novamall_add_column('mall_inventory', 'legacy_product_id', 'BIGINT GENERATED ALWAYS AS (IF(sku_id IS NULL, product_id, NULL)) STORED');
CALL novamall_add_column('mall_inventory_reservation', 'legacy_product_id', 'BIGINT GENERATED ALWAYS AS (IF(sku_id IS NULL, product_id, NULL)) STORED');

-- Reserve a deterministic ID only where neither the SKU ID nor default code exists.
INSERT INTO mall_product_sku
    (id, product_id, sku_code, name, spec_json, image_url, price, market_price, status, created_at, updated_at)
SELECT p.id, p.id, CONCAT('P', p.id, '-DEFAULT'), 'Default', JSON_OBJECT(),
       p.cover_image_url, p.price, p.market_price, p.status, p.created_at, p.updated_at
FROM mall_product p
WHERE p.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM mall_product_sku s WHERE s.sku_code = CONCAT('P', p.id, '-DEFAULT'))
  AND NOT EXISTS (SELECT 1 FROM mall_product_sku s WHERE s.id = p.id);

-- Fail visibly on an ID collision instead of silently skipping a product.
DROP PROCEDURE IF EXISTS novamall_check_defaults;
DELIMITER $$
CREATE PROCEDURE novamall_check_defaults()
BEGIN
    IF EXISTS (SELECT 1 FROM mall_product p WHERE p.deleted = 0 AND NOT EXISTS
               (SELECT 1 FROM mall_product_sku s WHERE s.product_id = p.id
                AND s.sku_code = CONCAT('P', p.id, '-DEFAULT'))) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Default SKU missing: resolve SKU ID collision and rerun';
    END IF;
END$$
DELIMITER ;
CALL novamall_check_defaults();
DROP PROCEDURE novamall_check_defaults;

UPDATE mall_inventory i JOIN mall_product_sku s
ON s.product_id = i.product_id AND s.sku_code = CONCAT('P', i.product_id, '-DEFAULT')
SET i.sku_id = s.id WHERE i.sku_id IS NULL;
UPDATE mall_inventory_reservation r JOIN mall_product_sku s
ON s.product_id = r.product_id AND s.sku_code = CONCAT('P', r.product_id, '-DEFAULT')
SET r.sku_id = s.id WHERE r.sku_id IS NULL;
UPDATE mall_order SET product_amount = total_amount
WHERE product_amount = 0 AND discount_amount = 0 AND freight_amount = 0 AND total_amount <> 0;

CALL novamall_index('mall_inventory', 'uk_mall_inventory_sku', 'UNIQUE INDEX uk_mall_inventory_sku (sku_id)');
CALL novamall_index('mall_inventory', 'uk_mall_inventory_legacy', 'UNIQUE INDEX uk_mall_inventory_legacy (legacy_product_id)');
CALL novamall_index('mall_inventory', 'idx_inventory_product', 'INDEX idx_inventory_product (product_id)');
CALL novamall_index('mall_inventory', 'uk_mall_inventory_product', NULL);
CALL novamall_index('mall_inventory_reservation', 'uk_mall_reservation_order_sku', 'UNIQUE INDEX uk_mall_reservation_order_sku (order_no, sku_id)');
CALL novamall_index('mall_inventory_reservation', 'uk_mall_reservation_legacy', 'UNIQUE INDEX uk_mall_reservation_legacy (order_no, legacy_product_id)');
CALL novamall_index('mall_inventory_reservation', 'idx_reservation_product', 'INDEX idx_reservation_product (product_id)');
CALL novamall_index('mall_inventory_reservation', 'uk_mall_reservation_order_product', NULL);
DROP PROCEDURE novamall_add_column;
DROP PROCEDURE novamall_index;
