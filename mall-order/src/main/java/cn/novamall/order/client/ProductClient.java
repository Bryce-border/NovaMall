package cn.novamall.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.Map;

@FeignClient(name = "mall-product", path = "/internal/products")
public interface ProductClient {

    @GetMapping("/skus/{id}")
    SkuSnapshot sku(@PathVariable("id") Long id);

    @GetMapping("/{id}/default-sku")
    SkuSnapshot defaultSku(@PathVariable("id") Long id);

    record SkuSnapshot(Long skuId, Long productId, String skuCode, String productName,
                       String skuName, Map<String, String> specs, String coverImageUrl,
                       BigDecimal price, BigDecimal marketPrice, Integer status) { }
}
