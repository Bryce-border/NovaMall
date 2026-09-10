package cn.novamall.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class InventoryApiModels {

    private InventoryApiModels() { }

    public record AddStockRequest(@NotNull @Positive Long productId, Long skuId, @NotNull @Positive Integer quantity) {
        public AddStockRequest(Long productId, Integer quantity) { this(productId, null, quantity); }
    }

    public record StockResponse(Long productId, Long skuId, Integer totalStock, Integer availableStock,
                                Integer lockedStock, Integer version) {
        public StockResponse(Long productId, Integer totalStock, Integer availableStock, Integer lockedStock, Integer version) { this(productId, null, totalStock, availableStock, lockedStock, version); }
    }

    public record ReserveRequest(@NotNull @Positive Long productId, Long skuId, @NotNull @Positive Integer quantity,
                                  @NotNull String orderNo) {
        public ReserveRequest(Long productId, Integer quantity, String orderNo) { this(productId, null, quantity, orderNo); }
    }

    public record ReserveResponse(boolean success, String message) { }

    public record ReleaseRequest(@NotNull String orderNo) { }
}
