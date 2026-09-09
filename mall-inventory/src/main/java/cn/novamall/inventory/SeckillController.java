package cn.novamall.inventory;

import cn.novamall.common.core.ApiResponse;
import cn.novamall.inventory.service.SeckillApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seckill")
@Validated
public class SeckillController {
    private final SeckillApplicationService seckillService;

    public SeckillController(SeckillApplicationService seckillService) {
        this.seckillService = seckillService;
    }

    @PostMapping("/{productId}/prepare")
    public ApiResponse<SeckillApiModels.PrepareResponse> prepare(
            @PathVariable @Positive Long productId,
            @Valid @RequestBody SeckillApiModels.PrepareRequest request) {
        return ApiResponse.ok(seckillService.prepare(productId, request.stock()));
    }

    @PostMapping("/{productId}")
    public ApiResponse<SeckillApiModels.SubmitResponse> submit(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable @Positive Long productId) {
        return ApiResponse.ok(seckillService.submit(userId, productId));
    }

    @GetMapping("/orders/{seckillNo}")
    public ApiResponse<SeckillApiModels.OrderResponse> get(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable String seckillNo) {
        return ApiResponse.ok(seckillService.get(seckillNo, userId));
    }
}
