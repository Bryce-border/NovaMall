package cn.novamall.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class InventoryApiModels {

    private InventoryApiModels() { }

    public record AddStockRequest(@NotNull @Positive Long productId, @NotNull @Positive Integer quantity) { }

    public record StockResponse(Long productId, Integer totalStock, Integer availableStock,
                                Integer lockedStock, Integer version) { }

    public record ReserveRequest(@NotNull @Positive Long productId, @NotNull @Positive Integer quantity,
                                  @NotNull String orderNo) { }

    public record ReserveResponse(boolean success, String message) { }

    public record ReleaseRequest(@NotNull String orderNo) { }
}
