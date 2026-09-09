package cn.novamall.product;

import cn.novamall.common.core.ApiResponse;
import cn.novamall.product.service.ProductApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@Validated
public class ProductController {

    private final ProductApplicationService productService;

    public ProductController(ProductApplicationService productService) {
        this.productService = productService;
    }

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("mall-product");
    }

    @GetMapping("/hot")
    public ApiResponse<java.util.List<ProductApiModels.HotProductResponse>> hot(
            @RequestParam(name = "limit", defaultValue = "10") @Min(1) @Max(50) long limit) {
        return ApiResponse.ok(productService.hot(limit));
    }

    @GetMapping("/sales")
    public ApiResponse<java.util.List<ProductApiModels.SalesProductResponse>> sales(
            @RequestParam(name = "limit", defaultValue = "10") @Min(1) @Max(50) long limit) {
        return ApiResponse.ok(productService.sales(limit));
    }

    @PostMapping
    public ApiResponse<ProductApiModels.ProductResponse> create(
            @Valid @RequestBody ProductApiModels.CreateProductRequest request) {
        return ApiResponse.ok(productService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductApiModels.ProductResponse> get(@PathVariable @Positive Long id) {
        return ApiResponse.ok(productService.get(id));
    }

    @GetMapping
    public ApiResponse<ProductApiModels.ProductPageResponse> page(
            @RequestParam(name = "page", defaultValue = "1") @Min(1) long page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) long size,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "keyword", required = false) String keyword) {
        return ApiResponse.ok(productService.page(page, size, categoryId, status, keyword));
    }

    @GetMapping("/search")
    public ApiResponse<ProductApiModels.ProductPageResponse> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "minPrice", required = false) @DecimalMin("0.00") java.math.BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) @DecimalMin("0.00") java.math.BigDecimal maxPrice,
            @RequestParam(name = "page", defaultValue = "1") @Min(1) long page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) long size) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("minPrice cannot exceed maxPrice");
        }
        return ApiResponse.ok(productService.search(page, size, keyword, categoryId, minPrice, maxPrice));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductApiModels.ProductResponse> update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody ProductApiModels.UpdateProductRequest request) {
        return ApiResponse.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        productService.delete(id);
        return ApiResponse.ok(null);
    }
}
