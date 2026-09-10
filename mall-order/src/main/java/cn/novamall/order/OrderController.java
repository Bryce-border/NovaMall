package cn.novamall.order;

import cn.novamall.common.core.ApiResponse;
import cn.novamall.order.service.OrderApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {

    private final OrderApplicationService orderService;

    public OrderController(OrderApplicationService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("mall-order");
    }

    @PostMapping
    public ApiResponse<OrderApiModels.OrderResponse> create(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @RequestHeader("X-User-Name") String userName,
            @Valid @RequestBody OrderApiModels.CreateOrderRequest request) {
        return ApiResponse.ok(orderService.create(userId, userName, request));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderApiModels.OrderResponse> get(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable String orderNo) {
        return ApiResponse.ok(orderService.get(userId, orderNo));
    }

    @GetMapping
    public ApiResponse<OrderApiModels.OrderPageResponse> page(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @RequestParam(name = "page", defaultValue = "1") long page,
            @RequestParam(name = "size", defaultValue = "10") long size,
            @RequestParam(required = false) String status) {
        if (page < 1 || size < 1 || size > 100) throw new IllegalArgumentException("invalid page or size");
        return ApiResponse.ok(orderService.page(userId, page, size, status));
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<OrderApiModels.OrderResponse> cancel(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable String orderNo) {
        return ApiResponse.ok(orderService.cancel(userId, orderNo));
    }

}
