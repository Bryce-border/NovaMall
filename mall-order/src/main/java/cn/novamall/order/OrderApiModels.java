package cn.novamall.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderApiModels {

    private OrderApiModels() { }

    public record CreateOrderRequest(
            @NotEmpty @Size(max = 100) List<@NotNull @Valid OrderItemRequest> items, @Positive Long addressId,
            @Positive Long couponId) {
        public CreateOrderRequest(List<@Valid OrderItemRequest> items) { this(items, null, null); }
        public CreateOrderRequest(List<@Valid OrderItemRequest> items, Long addressId) { this(items, addressId, null); }
    }

    public record OrderItemRequest(@Positive Long productId, @Positive Long skuId,
                                   @NotNull @Positive Integer quantity) {
        public OrderItemRequest(Long productId, Integer quantity) { this(productId, null, quantity); }

        @AssertTrue(message = "provide exactly one of productId or skuId")
        @JsonIgnore
        public boolean isReferenceValid() { return (productId == null) != (skuId == null); }
    }

    public record OrderResponse(Long id, String orderNo, Long userId, BigDecimal totalAmount,
                                String status, List<OrderItemResponse> items, LocalDateTime createdAt) { }

    public record OrderPageResponse(List<OrderSummaryResponse> items, long page, long size,
                                    long total, boolean hasNext) { }

    public record OrderSummaryResponse(String orderNo, String status, int itemCount,
                                       OrderItemResponse firstItem, BigDecimal totalAmount,
                                       LocalDateTime createdAt, String refundStatus) { }

    public record OrderItemResponse(Long productId, Long skuId, String productName, String coverImageUrl,
                                    String skuName, BigDecimal unitPrice, Integer quantity, BigDecimal amount) {
        public OrderItemResponse(Long productId, String productName, BigDecimal unitPrice, Integer quantity, BigDecimal amount) {
            this(productId, null, productName, null, null, unitPrice, quantity, amount);
        }
    }
}
