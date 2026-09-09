package cn.novamall.inventory.service;

import cn.novamall.inventory.InventoryApiModels;
import cn.novamall.inventory.entity.InventoryEntity;
import cn.novamall.inventory.entity.InventoryReservationEntity;
import cn.novamall.inventory.mapper.InventoryMapper;
import cn.novamall.inventory.mapper.InventoryReservationMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InventoryApplicationService {

    private static final String LOCKED = "LOCKED";

    private final InventoryMapper inventoryMapper;
    private final InventoryReservationMapper reservationMapper;

    public InventoryApplicationService(InventoryMapper inventoryMapper,
                                        InventoryReservationMapper reservationMapper) {
        this.inventoryMapper = inventoryMapper;
        this.reservationMapper = reservationMapper;
    }

    @Transactional
    public InventoryApiModels.StockResponse addStock(Long productId, Integer quantity) {
        InventoryEntity inventory = inventoryMapper.selectOne(Wrappers.<InventoryEntity>lambdaQuery()
                .eq(InventoryEntity::getProductId, productId));
        if (inventory == null) {
            LocalDateTime now = LocalDateTime.now();
            inventory = new InventoryEntity();
            inventory.setProductId(productId);
            inventory.setTotalStock(quantity);
            inventory.setAvailableStock(quantity);
            inventory.setLockedStock(0);
            inventory.setVersion(0);
            inventory.setCreatedAt(now);
            inventory.setUpdatedAt(now);
            inventoryMapper.insert(inventory);
        } else {
            inventoryMapper.increase(productId, quantity);
            inventory = inventoryMapper.selectOne(Wrappers.<InventoryEntity>lambdaQuery()
                    .eq(InventoryEntity::getProductId, productId));
        }
        return toResponse(inventory);
    }

    public InventoryApiModels.StockResponse get(Long productId) {
        return toResponse(requireInventory(productId));
    }

    @Transactional
    public InventoryApiModels.ReserveResponse reserve(InventoryApiModels.ReserveRequest request) {
        InventoryReservationEntity existing = reservationMapper.findByOrderAndProduct(
                request.orderNo(), request.productId());
        if (existing != null) {
            return new InventoryApiModels.ReserveResponse(LOCKED.equals(existing.getStatus()),
                    LOCKED.equals(existing.getStatus()) ? "already reserved" : "reservation released");
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            InventoryEntity inventory = findInventory(request.productId());
            if (inventory == null) {
                return new InventoryApiModels.ReserveResponse(false, "inventory not found");
            }
            if (inventory.getAvailableStock() < request.quantity()) {
                return new InventoryApiModels.ReserveResponse(false, "insufficient stock");
            }
            if (inventoryMapper.reserve(request.productId(), request.quantity(), inventory.getVersion()) == 1) {
                InventoryReservationEntity reservation = new InventoryReservationEntity();
                reservation.setOrderNo(request.orderNo());
                reservation.setProductId(request.productId());
                reservation.setQuantity(request.quantity());
                reservation.setStatus(LOCKED);
                reservation.setCreatedAt(LocalDateTime.now());
                reservation.setUpdatedAt(LocalDateTime.now());
                reservationMapper.insert(reservation);
                return new InventoryApiModels.ReserveResponse(true, "reserved");
            }
        }
        return new InventoryApiModels.ReserveResponse(false, "inventory is busy, please retry");
    }

    @Transactional
    public void release(String orderNo) {
        List<InventoryReservationEntity> reservations = reservationMapper.findLockedByOrderNo(orderNo);
        for (InventoryReservationEntity reservation : reservations) {
            if (reservationMapper.markReleased(reservation.getId()) == 1) {
                int affected = inventoryMapper.release(reservation.getProductId(), reservation.getQuantity());
                if (affected != 1) {
                    throw new IllegalStateException("failed to release inventory for " + orderNo);
                }
            }
        }
    }

    @Transactional
    public void confirm(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("orderNo is required");
        }
        List<InventoryReservationEntity> reservations = reservationMapper.findByOrderForUpdate(orderNo);
        if (reservations.isEmpty()) {
            throw new IllegalStateException("reservation not found for paid order: " + orderNo);
        }
        // Lock all rows before checking terminal states; a failure rolls back the entire order.
        for (InventoryReservationEntity reservation : reservations) {
            if (!LOCKED.equals(reservation.getStatus()) && !"CONFIRMED".equals(reservation.getStatus())) {
                throw new IllegalStateException("cannot confirm reservation in status: " + reservation.getStatus());
            }
        }
        for (InventoryReservationEntity reservation : reservations) {
            if ("CONFIRMED".equals(reservation.getStatus())) continue;
            if (reservationMapper.markConfirmed(reservation.getId()) != 1
                    || inventoryMapper.confirm(reservation.getProductId(), reservation.getQuantity()) != 1) {
                throw new IllegalStateException("failed to confirm inventory for " + orderNo);
            }
        }
    }

    private InventoryEntity requireInventory(Long productId) {
        InventoryEntity inventory = findInventory(productId);
        if (inventory == null) {
            throw new IllegalArgumentException("inventory not found: " + productId);
        }
        return inventory;
    }

    private InventoryEntity findInventory(Long productId) {
        return inventoryMapper.selectOne(Wrappers.<InventoryEntity>lambdaQuery()
                .eq(InventoryEntity::getProductId, productId));
    }

    private InventoryApiModels.StockResponse toResponse(InventoryEntity inventory) {
        return new InventoryApiModels.StockResponse(inventory.getProductId(), inventory.getTotalStock(),
                inventory.getAvailableStock(), inventory.getLockedStock(), inventory.getVersion());
    }
}
