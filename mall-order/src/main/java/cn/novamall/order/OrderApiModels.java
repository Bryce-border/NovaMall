package cn.novamall.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderApiModels {

    private OrderApiModels() { }

    public record CreateOrderRequest(
            @NotEmpty List<@Valid OrderItemRequest> items) { }

    public record OrderItemRequest(@NotNull @Positive Long productId, @NotNull @Positive Integer quantity) { }

    public record OrderResponse(Long id, String orderNo, Long userId, BigDecimal totalAmount,
                                String status, List<OrderItemResponse> items, LocalDateTime createdAt) { }

    public record OrderItemResponse(Long productId, String productName, BigDecimal unitPrice,
                                    Integer quantity, BigDecimal amount) { }
}
