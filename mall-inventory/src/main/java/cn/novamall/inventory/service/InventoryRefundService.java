package cn.novamall.inventory.service;

import cn.novamall.inventory.mapper.InventoryMapper;
import cn.novamall.inventory.mapper.InventoryReservationMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class InventoryRefundService {
    private final JdbcTemplate db;
    private final InventoryApplicationService inventory;
    private final InventoryReservationMapper reservations;
    private final InventoryMapper stocks;

    public InventoryRefundService(JdbcTemplate db, InventoryApplicationService inventory,
                                  InventoryReservationMapper reservations, InventoryMapper stocks) {
        this.db = db; this.inventory = inventory; this.reservations = reservations; this.stocks = stocks;
    }
    public record Line(Long productId, Long skuId, Integer quantity) { }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void restock(String no, String orderNo, List<Line> lines) {
        if (no == null || no.isBlank() || orderNo == null || orderNo.isBlank() || lines == null || lines.isEmpty())
            throw new IllegalArgumentException("invalid refund inventory event");
        // A refund may arrive before ORDER_PAID. Confirm under the same reservation locks first;
        // a later paid event will then be an idempotent no-op.
        inventory.confirm(orderNo);
        var reserved = reservations.findByOrderForUpdate(orderNo);
        Set<Long> seen = new HashSet<>();
        for (var line : lines) {
            if (line.productId() == null || line.quantity() == null || line.quantity() < 1)
                throw new IllegalArgumentException("invalid refund item");
            var reservation = reserved.stream().filter(r -> line.productId().equals(r.getProductId())
                    && (line.skuId() == null || line.skuId().equals(r.getSkuId()))).toList();
            if (reservation.size() != 1 || !seen.add(reservation.get(0).getId()))
                throw new IllegalStateException("refund item reservation missing or ambiguous");
            var r = reservation.get(0);
            var existing = db.queryForList("SELECT quantity FROM mall_inventory_refund WHERE refund_no=? AND reservation_id=?", no, r.getId());
            if (!existing.isEmpty()) {
                if (((Number)existing.get(0).get("quantity")).intValue() != line.quantity())
                    throw new IllegalStateException("refund inventory replay parameters conflict");
                continue;
            }
            long restored = db.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM mall_inventory_refund WHERE reservation_id=?", Long.class, r.getId());
            if (restored + line.quantity() > r.getQuantity()) throw new IllegalStateException("refund restock exceeds original reservation");
            int changed = r.getSkuId() == null ? stocks.increase(r.getProductId(), line.quantity()) : stocks.increaseSku(r.getSkuId(), line.quantity());
            if (changed != 1) throw new IllegalStateException("refund stock row missing");
            db.update("INSERT INTO mall_inventory_refund(refund_no,reservation_id,quantity,created_at) VALUES(?,?,?,NOW(3))",
                    no, r.getId(), line.quantity());
        }
    }
}
