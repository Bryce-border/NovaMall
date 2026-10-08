package cn.novamall.product;

import cn.novamall.common.core.ApiResponse;
import cn.novamall.product.service.ProductRankingService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@Validated
public class ProductSalesCountController {
    private final ProductRankingService rankings;

    public ProductSalesCountController(ProductRankingService rankings) { this.rankings = rankings; }

    @GetMapping("/api/products/sales-counts")
    public ApiResponse<List<ProductApiModels.SalesCountResponse>> counts(
            @RequestParam @NotEmpty @Size(max = 100) List<@Positive Long> productIds) {
        return ApiResponse.ok(rankings.salesCounts(productIds));
    }
}
