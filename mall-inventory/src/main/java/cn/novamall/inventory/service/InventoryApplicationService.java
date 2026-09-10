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
        return addStock(productId, null, quantity);
    }

    @Transactional
    public InventoryApiModels.StockResponse addStock(Long productId, Long skuId, Integer quantity) {
        validate(productId, skuId, quantity);
        InventoryEntity inventory = skuId == null ? findInventory(productId) : findInventoryBySku(skuId);
        if (inventory == null) {
            LocalDateTime now = LocalDateTime.now();
            inventory = new InventoryEntity();
            inventory.setProductId(productId);
            inventory.setSkuId(skuId);
            inventory.setTotalStock(quantity);
            inventory.setAvailableStock(quantity);
            inventory.setLockedStock(0);
            inventory.setVersion(0);
            inventory.setCreatedAt(now);
            inventory.setUpdatedAt(now);
            inventoryMapper.insert(inventory);
        } else {
            requireOwner(inventory, productId);
            int affected = inventory.getSkuId() == null ? inventoryMapper.increase(productId, quantity)
                    : inventoryMapper.increaseSku(inventory.getSkuId(), quantity);
            if (affected != 1) throw new IllegalStateException("inventory increase failed");
            inventory = inventory.getSkuId() == null ? findInventory(productId) : findInventoryBySku(inventory.getSkuId());
        }
        return toResponse(inventory);
    }

    public InventoryApiModels.StockResponse get(Long productId) {
        return toResponse(requireInventory(productId));
    }

    public InventoryApiModels.StockResponse getBySku(Long skuId) {
        if (skuId == null || skuId <= 0) throw new IllegalArgumentException("positive skuId required");
        InventoryEntity inventory = findInventoryBySku(skuId);
        if (inventory == null) throw new IllegalArgumentException("inventory not found for SKU: " + skuId);
        return toResponse(inventory);
    }

    @Transactional
    public InventoryApiModels.ReserveResponse reserve(InventoryApiModels.ReserveRequest request) {
        validate(request.productId(), request.skuId(), request.quantity());
        if (request.orderNo() == null || request.orderNo().isBlank() || request.orderNo().length() > 32) {
            throw new IllegalArgumentException("orderNo must contain 1 to 32 characters");
        }
        InventoryReservationEntity existing = request.skuId() == null
                ? reservationMapper.findByOrderAndProduct(request.orderNo(), request.productId())
                : reservationMapper.findByOrderAndSku(request.orderNo(), request.skuId());
        if (existing != null) {
            if (!request.productId().equals(existing.getProductId()) || !request.quantity().equals(existing.getQuantity())
                    || (request.skuId() != null && !request.skuId().equals(existing.getSkuId()))) {
                return new InventoryApiModels.ReserveResponse(false, "reservation parameters conflict");
            }
            return new InventoryApiModels.ReserveResponse(LOCKED.equals(existing.getStatus()),
                    LOCKED.equals(existing.getStatus()) ? "already reserved" : "reservation released");
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            InventoryEntity inventory = request.skuId() == null ? findInventory(request.productId()) : findInventoryBySku(request.skuId());
            if (inventory == null) {
                return new InventoryApiModels.ReserveResponse(false, "inventory not found");
            }
            requireOwner(inventory, request.productId());
            if (inventory.getAvailableStock() < request.quantity()) {
                return new InventoryApiModels.ReserveResponse(false, "insufficient stock");
            }
            int affected = inventory.getSkuId() == null
                    ? inventoryMapper.reserve(request.productId(), request.quantity(), inventory.getVersion())
                    : inventoryMapper.reserveSku(inventory.getSkuId(), request.quantity(), inventory.getVersion());
            if (affected == 1) {
                InventoryReservationEntity reservation = new InventoryReservationEntity();
                reservation.setOrderNo(request.orderNo());
                reservation.setProductId(request.productId());
                reservation.setSkuId(inventory.getSkuId());
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
                int affected = reservation.getSkuId() == null
                        ? inventoryMapper.release(reservation.getProductId(), reservation.getQuantity())
                        : inventoryMapper.releaseSku(reservation.getSkuId(), reservation.getQuantity());
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
                    || (reservation.getSkuId() == null
                    ? inventoryMapper.confirm(reservation.getProductId(), reservation.getQuantity())
                    : inventoryMapper.confirmSku(reservation.getSkuId(), reservation.getQuantity())) != 1) {
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
        List<InventoryEntity> rows = inventoryMapper.selectList(Wrappers.<InventoryEntity>lambdaQuery()
                .eq(InventoryEntity::getProductId, productId).last("LIMIT 2"));
        if (rows.size() > 1) throw new IllegalArgumentException("multiple SKU inventories; specify skuId");
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void validate(Long productId, Long skuId, Integer quantity) {
        if (productId == null || productId <= 0 || (skuId != null && skuId <= 0)
                || quantity == null || quantity <= 0) throw new IllegalArgumentException("positive IDs and quantity required");
    }

    private void requireOwner(InventoryEntity inventory, Long productId) {
        if (!productId.equals(inventory.getProductId())) throw new IllegalArgumentException("SKU does not belong to product");
    }

    private InventoryEntity findInventoryBySku(Long skuId) {
        return inventoryMapper.selectOne(Wrappers.<InventoryEntity>lambdaQuery().eq(InventoryEntity::getSkuId, skuId));
    }

    private InventoryApiModels.StockResponse toResponse(InventoryEntity inventory) {
        return new InventoryApiModels.StockResponse(inventory.getProductId(), inventory.getSkuId(), inventory.getTotalStock(),
                inventory.getAvailableStock(), inventory.getLockedStock(), inventory.getVersion());
    }
}
