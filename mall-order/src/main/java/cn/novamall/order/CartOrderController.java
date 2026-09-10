package cn.novamall.order;

import cn.novamall.common.core.ApiResponse;
import cn.novamall.order.service.CartOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@Validated
public class CartOrderController {
    private final CartOrderService service;

    public CartOrderController(CartOrderService service) { this.service = service; }

    @PostMapping("/checkout")
    public ApiResponse<OrderApiModels.OrderResponse> checkout(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @RequestHeader("X-User-Name") String userName,
            @Valid @RequestBody CartOrderApiModels.CheckoutRequest request) {
        return ApiResponse.ok(service.checkout(userId, userName, request));
    }
}
