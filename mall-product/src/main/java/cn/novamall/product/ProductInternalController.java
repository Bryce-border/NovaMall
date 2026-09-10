package cn.novamall.product;

import cn.novamall.product.service.SkuApplicationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final SkuApplicationService skus;
    private final ObjectMapper json;

    public ProductInternalController(SkuApplicationService skus, ObjectMapper json) {
        this.skus = skus;
        this.json = json;
    }

    @GetMapping("/{id}")
    public ProductSnapshot get(@PathVariable @Positive Long id) {
        SkuApiModels.SkuResponse sku = skus.defaultForProduct(id);
        try {
            return new ProductSnapshot(sku.productId(), sku.productName(), sku.price(), sku.status(),
                    sku.coverImageUrl(), sku.skuId(), sku.skuName(), json.writeValueAsString(sku.specs()));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("cannot serialize SKU specifications", ex);
        }
    }

    public record ProductSnapshot(Long id, String name, java.math.BigDecimal price, Integer status,
                                  String coverImageUrl, Long defaultSkuId, String skuName, String specJson) { }
}
