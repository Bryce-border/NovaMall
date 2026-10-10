package cn.novamall.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public final class CartOrderApiModels {
    private CartOrderApiModels() { }

    public record CheckoutRequest(
            @NotNull @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestKey,
            @NotNull @Positive Long addressId, @Positive Long couponId) {
        public CheckoutRequest(String requestKey, Long addressId) { this(requestKey, addressId, null); }
    }
}
