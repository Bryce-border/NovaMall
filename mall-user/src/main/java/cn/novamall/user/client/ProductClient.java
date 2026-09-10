package cn.novamall.user.client;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.math.BigDecimal;
@FeignClient(name="mall-product", path="/internal/products/skus")
public interface ProductClient {
 @GetMapping("/{skuId}") SkuSnapshot get(@PathVariable("skuId") Long skuId);
 record SkuSnapshot(Long skuId, Long productId, String skuCode, String productName, String skuName, java.util.Map<String,String> specs, String coverImageUrl, BigDecimal price, BigDecimal marketPrice, Integer status) {}
}
