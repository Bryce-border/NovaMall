package cn.novamall.product.service;

import cn.novamall.product.SkuApiModels.SkuResponse;
import cn.novamall.product.entity.ProductEntity;
import cn.novamall.product.entity.ProductSkuEntity;
import cn.novamall.product.mapper.ProductMapper;
import cn.novamall.product.mapper.ProductSkuMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class SkuApplicationService {
    private final ProductMapper products;
    private final ProductSkuMapper skus;
    private final ObjectMapper json;

    public SkuApplicationService(ProductMapper products, ProductSkuMapper skus, ObjectMapper json) {
        this.products = products;
        this.skus = skus;
        this.json = json;
    }

    // Checkout must read current database values, independent of the browsing cache.
    @Transactional(readOnly = true)
    public SkuResponse get(Long skuId) {
        ProductSkuEntity sku = skus.selectById(skuId);
        if (sku == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "sku not found");
        ProductEntity product = products.selectById(sku.getProductId());
        if (product == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found");
        return response(product, sku);
    }

    @Transactional(readOnly = true)
    public SkuResponse defaultForProduct(Long productId) {
        ProductEntity product = products.selectById(productId);
        if (product == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found");
        ProductSkuEntity sku = skus.selectOne(Wrappers.<ProductSkuEntity>lambdaQuery()
                .eq(ProductSkuEntity::getProductId, productId)
                .eq(ProductSkuEntity::getSkuCode, "P" + productId + "-DEFAULT"));
        if (sku == null) throw new IllegalStateException("default SKU missing; apply database migration");
        return response(product, sku);
    }

    @Transactional(readOnly = true)
    public List<SkuResponse> list(Long productId) {
        ProductEntity product = products.selectById(productId);
        if (product == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found");
        return skus.selectList(Wrappers.<ProductSkuEntity>lambdaQuery()
                .eq(ProductSkuEntity::getProductId, productId).orderByAsc(ProductSkuEntity::getId))
                .stream().map(sku -> response(product, sku)).toList();
    }

    private SkuResponse response(ProductEntity product, ProductSkuEntity sku) {
        try {
            java.util.Map<String, String> specs = sku.getSpecJson() == null ? java.util.Map.of()
                    : json.readValue(sku.getSpecJson(), new TypeReference<java.util.Map<String, String>>() { });
            int status = Integer.valueOf(1).equals(product.getStatus())
                    && Integer.valueOf(1).equals(sku.getStatus()) ? 1 : 0;
            return new SkuResponse(sku.getId(), product.getId(), sku.getSkuCode(), product.getName(),
                    sku.getName(), specs, sku.getImageUrl() == null ? product.getCoverImageUrl() : sku.getImageUrl(),
                    sku.getPrice(), sku.getMarketPrice(), status);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("invalid stored SKU specifications", ex);
        }
    }
}
