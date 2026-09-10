package cn.novamall.product;

import cn.novamall.common.core.ApiResponse;
import cn.novamall.product.service.SkuApplicationService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
public class SkuController {
    private final SkuApplicationService service;
    public SkuController(SkuApplicationService service) { this.service = service; }

    @GetMapping("/internal/products/skus/{skuId}")
    public SkuApiModels.SkuResponse internal(@PathVariable @Positive Long skuId) {
        return service.get(skuId);
    }

    @GetMapping("/internal/products/{productId}/default-sku")
    public SkuApiModels.SkuResponse defaultSku(@PathVariable @Positive Long productId) {
        return service.defaultForProduct(productId);
    }

    @GetMapping("/api/products/{productId}/skus")
    public ApiResponse<java.util.List<SkuApiModels.SkuResponse>> list(@PathVariable @Positive Long productId) {
        return ApiResponse.ok(service.list(productId));
    }
}
