package cn.novamall.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ProductApiModels {

    private ProductApiModels() { }

    public record CreateProductRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 255) String subtitle,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull Integer status) { }

    public record UpdateProductRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 255) String subtitle,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull Integer status) { }

    public record ProductResponse(
            Long id,
            Long categoryId,
            String name,
            String subtitle,
            BigDecimal price,
            Integer status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) { }

    public record ProductPageResponse(List<ProductResponse> items, long total, long page, long size) { }

    public record ProductDetailResponse(Long id, Long categoryId, String name, String subtitle,
                                        String description, String coverImageUrl, BigDecimal price,
                                        BigDecimal marketPrice, Integer status,
                                        LocalDateTime createdAt, LocalDateTime updatedAt) { }

    public record HotProductResponse(Long productId, String name, BigDecimal price, Double score) { }

    public record SalesProductResponse(Long productId, String name, BigDecimal price, Double sales) { }
}
