package cn.novamall.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class CartCheckoutModels {
    private CartCheckoutModels() { }

    public record Item(Long cartItemId, Long skuId, Integer quantity, Long version) { }

    public record CleanupItem(@NotNull @Positive Long cartItemId,
                              @NotNull @PositiveOrZero Long version) { }

    public record CleanupRequest(@NotNull @Positive Long userId,
                                 @NotNull @Size(max = 100) List<@NotNull @Valid CleanupItem> items) { }
}
