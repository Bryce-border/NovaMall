package cn.novamall.order;

import cn.novamall.common.core.ApiResponse;
import cn.novamall.order.service.OrderDetailService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class OrderDetailController {
    private final OrderDetailService details;

    public OrderDetailController(OrderDetailService details) { this.details = details; }

    @GetMapping("/api/orders/{orderNo}/detail")
    public ApiResponse<OrderDetailApiModels.Detail> get(
            @RequestHeader("X-User-Id") @Positive Long userId, @PathVariable String orderNo) {
        return ApiResponse.ok(details.get(userId, orderNo));
    }
}
