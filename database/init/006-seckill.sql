CREATE TABLE IF NOT EXISTS mall_seckill_order (
    id BIGINT NOT NULL PRIMARY KEY,
    seckill_no VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL,
    message_id VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_mall_seckill_order_no (seckill_no),
    UNIQUE KEY uk_mall_seckill_user_product (user_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
