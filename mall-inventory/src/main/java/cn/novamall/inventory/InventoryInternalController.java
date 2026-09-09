package cn.novamall.inventory;

import cn.novamall.inventory.service.InventoryApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/inventory")
public class InventoryInternalController {

    private final InventoryApplicationService inventoryService;

    public InventoryInternalController(InventoryApplicationService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/reserve")
    public InventoryApiModels.ReserveResponse reserve(
            @Valid @RequestBody InventoryApiModels.ReserveRequest request) {
        return inventoryService.reserve(request);
    }

    @PostMapping("/release")
    public void release(@Valid @RequestBody InventoryApiModels.ReleaseRequest request) {
        inventoryService.release(request.orderNo());
    }
}
