package cn.novamall.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;

@FeignClient(name = "mall-product", path = "/internal/products")
public interface ProductClient {

    @GetMapping("/{id}")
    ProductSnapshot get(@PathVariable("id") Long id);

    record ProductSnapshot(Long id, String name, BigDecimal price, Integer status) { }
}
