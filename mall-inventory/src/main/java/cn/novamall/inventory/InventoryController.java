package cn.novamall.inventory;

import cn.novamall.common.core.ApiResponse;
import cn.novamall.inventory.service.InventoryApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@Validated
public class InventoryController {

    private final InventoryApplicationService inventoryService;

    public InventoryController(InventoryApplicationService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("mall-inventory");
    }

    @PostMapping
    public ApiResponse<InventoryApiModels.StockResponse> addStock(
            @Valid @RequestBody InventoryApiModels.AddStockRequest request) {
        return ApiResponse.ok(inventoryService.addStock(request.productId(), request.quantity()));
    }

    @GetMapping("/{productId}")
    public ApiResponse<InventoryApiModels.StockResponse> get(
            @PathVariable @Positive Long productId) {
        return ApiResponse.ok(inventoryService.get(productId));
    }
}
