package cn.novamall.order;

import cn.novamall.common.core.ApiResponse;
import cn.novamall.order.service.OrderApplicationService;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@ConditionalOnProperty(name = "novamall.fulfillment.mock-enabled", havingValue = "true")
public class MockFulfillmentController {
    private final OrderApplicationService orderService;

    public MockFulfillmentController(OrderApplicationService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/orders/{orderNo}/ship")
    public ApiResponse<OrderApiModels.OrderResponse> ship(
            @RequestHeader("X-User-Id") @Positive Long userId, @PathVariable String orderNo) {
        return ApiResponse.ok(orderService.ship(userId, orderNo));
    }

    @PostMapping("/api/orders/{orderNo}/complete")
    public ApiResponse<OrderApiModels.OrderResponse> complete(
            @RequestHeader("X-User-Id") @Positive Long userId, @PathVariable String orderNo) {
        return ApiResponse.ok(orderService.complete(userId, orderNo));
    }
}
