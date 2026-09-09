package cn.novamall.order;

import cn.novamall.common.core.ApiResponse;
import cn.novamall.order.service.OrderApplicationService;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@ConditionalOnProperty(name = "novamall.payment.mock-enabled", havingValue = "true")
public class MockPaymentController {
    private final OrderApplicationService orderService;

    public MockPaymentController(OrderApplicationService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/api/orders/{orderNo}/pay")
    public ApiResponse<OrderApiModels.OrderResponse> pay(
            @RequestHeader("X-User-Id") @Positive Long userId, @PathVariable String orderNo) {
        return ApiResponse.ok(orderService.pay(userId, orderNo));
    }
}
