package cn.novamall.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserApiModels {

    private UserApiModels() { }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 32) String username,
            @NotBlank @Size(min = 8, max = 64) String password,
            @NotBlank @Size(max = 64) String nickname) { }

    public record RegisterResponse(Long id, String username, String nickname) { }

    public record AddressRequest(@NotBlank String receiverName, @NotBlank String receiverPhone,
                                  @NotBlank String province, @NotBlank String city, @NotBlank String district,
                                  @NotBlank String detailAddress, String postalCode, Boolean isDefault) { }
    public record AddressResponse(Long id, String receiverName, String receiverPhone, String province,
                                  String city, String district, String detailAddress, String postalCode,
                                  boolean isDefault) { }
    public record CartItemRequest(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Long skuId,
                                  @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Integer quantity) {
        public CartItemRequest(Long skuId, Long productId, Integer quantity) { this(skuId, quantity); }
    }
    public record CartUpdateRequest(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Positive Integer quantity) { }
    public record CartItemResponse(Long id, Long productId, Long skuId, Integer quantity, boolean selected) { }
}
