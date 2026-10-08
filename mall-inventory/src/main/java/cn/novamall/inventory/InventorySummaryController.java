package cn.novamall.inventory;

import cn.novamall.common.core.ApiResponse;
import cn.novamall.inventory.entity.InventoryEntity;
import cn.novamall.inventory.mapper.InventoryMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@Validated
public class InventorySummaryController {
    private final InventoryMapper inventories;

    public InventorySummaryController(InventoryMapper inventories) { this.inventories = inventories; }

    @GetMapping("/api/inventory/summary")
    public ApiResponse<List<Summary>> summary(
            @RequestParam @NotEmpty @Size(max = 100) List<@Positive Long> productIds) {
        Map<Long, long[]> totals = new LinkedHashMap<>();
        productIds.forEach(id -> totals.putIfAbsent(id, new long[3]));
        // Only SKU stock can be purchased through checkout. Legacy product-only stock is excluded.
        for (InventoryEntity inventory : inventories.selectList(Wrappers.<InventoryEntity>lambdaQuery()
                .in(InventoryEntity::getProductId, productIds).isNotNull(InventoryEntity::getSkuId))) {
            long[] total = totals.get(inventory.getProductId());
            total[0] += inventory.getAvailableStock();
            total[1] += inventory.getLockedStock();
            total[2] += inventory.getTotalStock();
        }
        return ApiResponse.ok(totals.entrySet().stream().map(entry -> new Summary(entry.getKey().toString(),
                entry.getValue()[0], entry.getValue()[1], entry.getValue()[2])).toList());
    }

    public record Summary(String productId, long availableStock, long lockedStock, long totalStock) { }
}
