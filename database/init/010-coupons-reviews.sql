CREATE TABLE IF NOT EXISTS mall_coupon_campaign (
 id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL, kind VARCHAR(16) NOT NULL,
 threshold_amount DECIMAL(12,2) NOT NULL, discount_amount DECIMAL(12,2) NOT NULL,
 pack_price DECIMAL(12,2) NOT NULL DEFAULT 0, pack_size INT NOT NULL DEFAULT 1,
 total_quantity INT NOT NULL, issued_quantity INT NOT NULL DEFAULT 0,
 opens_at DATETIME NOT NULL, closes_at DATETIME NOT NULL, expires_at DATETIME NOT NULL,
 status TINYINT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL,
 KEY idx_coupon_campaign_time(status, opens_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS mall_coupon_claim (
 id BIGINT PRIMARY KEY, campaign_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 paid_amount DECIMAL(12,2) NOT NULL, simulated TINYINT NOT NULL DEFAULT 1,
 created_at DATETIME NOT NULL, UNIQUE KEY uk_coupon_claim(campaign_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS mall_user_coupon (
 id BIGINT PRIMARY KEY, claim_id BIGINT NOT NULL, campaign_id BIGINT NOT NULL,
 user_id BIGINT NOT NULL, name VARCHAR(100) NOT NULL,
 threshold_amount DECIMAL(12,2) NOT NULL, discount_amount DECIMAL(12,2) NOT NULL,
 expires_at DATETIME NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
 order_no VARCHAR(32) NULL, created_at DATETIME NOT NULL,
 KEY idx_user_coupon(user_id,status), KEY idx_coupon_order(order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS mall_checkout_coupon (
 user_id BIGINT NOT NULL, request_key VARCHAR(36) NOT NULL, coupon_id BIGINT NULL,
 PRIMARY KEY(user_id,request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS mall_product_review (
 id BIGINT PRIMARY KEY, product_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 user_name VARCHAR(64) NOT NULL, rating INT NOT NULL, content VARCHAR(1000) NOT NULL,
 created_at DATETIME NOT NULL, UNIQUE KEY uk_review_buyer(product_id,user_id),
 KEY idx_review_product(product_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
