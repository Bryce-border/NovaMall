package cn.novamall.product;

import cn.novamall.product.service.ProductApplicationService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/products")
@Validated
public class ProductInternalController {

    private final ProductApplicationService productService;

    public ProductInternalController(ProductApplicationService productService) {
        this.productService = productService;
    }

    @GetMapping("/{id}")
    public ProductSnapshot get(@PathVariable @Positive Long id) {
        ProductApiModels.ProductResponse product = productService.getAvailable(id);
        return new ProductSnapshot(product.id(), product.name(), product.price(), product.status());
    }

    public record ProductSnapshot(Long id, String name, java.math.BigDecimal price, Integer status) { }
}
