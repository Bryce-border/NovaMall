-- Requires 011. Apply while order/product writes are stopped; rerunning preserves projections.
CREATE TABLE IF NOT EXISTS mall_refund_return (
    refund_no VARCHAR(32) NOT NULL PRIMARY KEY,
    original_status VARCHAR(32) NOT NULL,
    receiver_name VARCHAR(64) NULL,
    receiver_phone VARCHAR(32) NULL,
    return_address VARCHAR(255) NULL,
    carrier VARCHAR(64) NULL,
    tracking_no VARCHAR(64) NULL,
    shipped_at DATETIME(3) NULL,
    inspection_result VARCHAR(16) NULL,
    inspection_note VARCHAR(500) NULL,
    inspector_id BIGINT NULL,
    inspected_at DATETIME(3) NULL,
    version INT NOT NULL DEFAULT 0,
    inspection_hash CHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    CONSTRAINT fk_return_refund FOREIGN KEY (refund_no) REFERENCES mall_refund(refund_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mall_refund_return_item (
    refund_no VARCHAR(32) NOT NULL,
    order_item_id BIGINT NOT NULL,
    restock_quantity INT NOT NULL,
    PRIMARY KEY (refund_no,order_item_id),
    CONSTRAINT fk_return_item FOREIGN KEY (refund_no,order_item_id) REFERENCES mall_refund_item(refund_no,order_item_id),
    CONSTRAINT chk_return_restock CHECK (restock_quantity>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Order service owns source versions; product service owns its durable sales projection.
CREATE TABLE IF NOT EXISTS mall_order_sales_version (
    order_no VARCHAR(32) NOT NULL PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS mall_product_sales_order (
    order_no VARCHAR(32) NOT NULL PRIMARY KEY, version BIGINT NOT NULL DEFAULT -1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS mall_product_sales_line (
    order_no VARCHAR(32) NOT NULL, product_id BIGINT NOT NULL, quantity BIGINT NOT NULL,
    PRIMARY KEY (order_no,product_id), KEY idx_sales_product (product_id),
    CONSTRAINT chk_sales_line CHECK (quantity>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS mall_product_sales_total (
    product_id BIGINT NOT NULL PRIMARY KEY, quantity BIGINT NOT NULL,
    KEY idx_sales_ranking (quantity,product_id), CONSTRAINT chk_sales_total CHECK (quantity>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed only orders not already projected. Version 0 is the upgrade baseline.
INSERT INTO mall_product_sales_line(order_no,product_id,quantity)
SELECT i.order_no,i.product_id,SUM(i.quantity-COALESCE(r.qty,0))
FROM mall_order_item i JOIN mall_order o ON o.order_no=i.order_no
LEFT JOIN (SELECT ri.order_item_id,SUM(ri.quantity) qty FROM mall_refund_item ri
 JOIN mall_refund r ON r.refund_no=ri.refund_no WHERE r.status='SUCCESS' GROUP BY ri.order_item_id) r ON r.order_item_id=i.id
WHERE o.status='COMPLETED' AND NOT EXISTS(SELECT 1 FROM mall_product_sales_order s WHERE s.order_no=i.order_no)
GROUP BY i.order_no,i.product_id
ON DUPLICATE KEY UPDATE quantity=VALUES(quantity);
INSERT IGNORE INTO mall_product_sales_order(order_no,version) SELECT order_no,0 FROM mall_order WHERE status='COMPLETED';
INSERT IGNORE INTO mall_order_sales_version(order_no,version) SELECT order_no,0 FROM mall_order WHERE status='COMPLETED';
INSERT INTO mall_product_sales_total(product_id,quantity)
SELECT product_id,SUM(quantity) FROM mall_product_sales_line GROUP BY product_id
ON DUPLICATE KEY UPDATE quantity=VALUES(quantity);
