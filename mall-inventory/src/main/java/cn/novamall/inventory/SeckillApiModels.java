package cn.novamall.inventory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public final class SeckillApiModels {
    private SeckillApiModels() { }
    public record PrepareRequest(@NotNull @Positive Integer stock) { }
    public record PrepareResponse(Long productId, Integer stock, String stockKey) { }
    public record SubmitResponse(String seckillNo, String status, String message) { }
    public record OrderResponse(String seckillNo, Long userId, Long productId, Integer quantity,
                                String status, LocalDateTime createdAt) { }
}
