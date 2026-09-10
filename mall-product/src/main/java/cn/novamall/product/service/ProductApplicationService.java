package cn.novamall.product.service;

import cn.novamall.product.ProductApiModels;
import cn.novamall.product.entity.CategoryEntity;
import cn.novamall.product.entity.ProductEntity;
import cn.novamall.product.mapper.CategoryMapper;
import cn.novamall.product.mapper.ProductMapper;
import cn.novamall.product.mapper.ProductSkuMapper;
import cn.novamall.product.entity.ProductSkuEntity;
import cn.novamall.common.id.IdGenerator;
import cn.novamall.product.entity.ProductDocument;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class ProductApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ProductApplicationService.class);
    private static final String CACHE_KEY_PREFIX = "product:detail:";
    private static final String HOT_KEY = "product:hot";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductSearchService searchService;
    private final ProductRankingService rankingService;
    private final ProductSkuMapper skuMapper;

    public ProductApplicationService(ProductMapper productMapper, CategoryMapper categoryMapper,
                                     StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                     ProductSearchService searchService, ProductRankingService rankingService,
                                     ProductSkuMapper skuMapper) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.searchService = searchService;
        this.rankingService = rankingService;
        this.skuMapper = skuMapper;
    }

    @Transactional
    public ProductApiModels.ProductResponse create(ProductApiModels.CreateProductRequest request) {
        requireActiveCategory(request.categoryId());
        validateStatus(request.status());
        LocalDateTime now = LocalDateTime.now();
        ProductEntity product = new ProductEntity();
        product.setCategoryId(request.categoryId());
        product.setName(request.name());
        product.setSubtitle(request.subtitle());
        product.setPrice(request.price());
        product.setStatus(request.status());
        product.setDeleted(0);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        productMapper.insert(product);
        createDefaultSku(product, now);
        index(product);
        return toResponse(product);
    }

    public ProductApiModels.ProductResponse get(Long id) {
        String cached = readCache(id);
        if (cached != null && !cached.isBlank()) {
            ProductApiModels.ProductResponse response = fromCache(cached);
            recordView(id);
            return response;
        }
        ProductEntity product = productMapper.selectById(id);
        if (product == null) {
            throw new IllegalArgumentException("product not found: " + id);
        }
        ProductApiModels.ProductResponse response = toResponse(product);
        writeCache(id, response);
        recordView(id);
        return response;
    }

    public List<ProductApiModels.HotProductResponse> hot(long limit) {
        try {
            var entries = redisTemplate.opsForZSet().reverseRangeWithScores(HOT_KEY, 0, limit - 1);
            if (entries == null || entries.isEmpty()) return List.of();
            List<Long> ids = entries.stream()
                    .map(entry -> Long.valueOf(String.valueOf(entry.getValue())))
                    .toList();
            Map<Long, ProductEntity> products = productMapper.selectBatchIds(ids).stream()
                    .filter(product -> Integer.valueOf(1).equals(product.getStatus()))
                    .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
            return ids.stream().filter(products::containsKey).map(id -> {
                ProductEntity product = products.get(id);
                Double score = entries.stream()
                        .filter(entry -> String.valueOf(id).equals(String.valueOf(entry.getValue())))
                        .map(org.springframework.data.redis.core.ZSetOperations.TypedTuple::getScore)
                        .findFirst().orElse(0D);
                return new ProductApiModels.HotProductResponse(product.getId(), product.getName(),
                        product.getPrice(), score);
            }).toList();
        } catch (RuntimeException ex) {
            log.warn("product hot ranking unavailable", ex);
            return List.of();
        }
    }

    public List<ProductApiModels.SalesProductResponse> sales(long limit) {
        try {
            return rankingService.topSales(limit);
        } catch (RuntimeException ex) {
            log.warn("product sales ranking unavailable", ex);
            return List.of();
        }
    }

    public ProductApiModels.ProductResponse getAvailable(Long id) {
        ProductApiModels.ProductResponse product = get(id);
        if (!Integer.valueOf(1).equals(product.status())) {
            throw new IllegalArgumentException("product is not available: " + id);
        }
        return product;
    }

    public ProductApiModels.ProductDetailResponse detail(Long id) {
        ProductEntity product = productMapper.selectById(id);
        if (product == null) throw new IllegalArgumentException("product not found: " + id);
        return new ProductApiModels.ProductDetailResponse(product.getId(), product.getCategoryId(), product.getName(),
                product.getSubtitle(), product.getDescription(), product.getCoverImageUrl(), product.getPrice(),
                product.getMarketPrice(), product.getStatus(), product.getCreatedAt(), product.getUpdatedAt());
    }

    public ProductApiModels.ProductPageResponse page(long page, long size, Long categoryId,
                                                      Integer status, String keyword) {
        LambdaQueryWrapper<ProductEntity> query = new LambdaQueryWrapper<>();
        query.eq(categoryId != null, ProductEntity::getCategoryId, categoryId)
                .eq(status != null, ProductEntity::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                        .like(ProductEntity::getName, keyword.trim())
                        .or()
                        .like(ProductEntity::getSubtitle, keyword.trim()))
                .orderByDesc(ProductEntity::getCreatedAt);
        Page<ProductEntity> result = productMapper.selectPage(new Page<>(page, size), query);
        List<ProductApiModels.ProductResponse> items = result.getRecords().stream()
                .map(this::toResponse)
                .toList();
        return new ProductApiModels.ProductPageResponse(items, result.getTotal(), page, size);
    }

    public ProductApiModels.ProductPageResponse search(long page, long size, String keyword,
                                                        Long categoryId, java.math.BigDecimal minPrice,
                                                        java.math.BigDecimal maxPrice) {
        try {
            return searchFromElastic(page, size, keyword, categoryId, minPrice, maxPrice);
        } catch (RuntimeException ex) {
            log.warn("elasticsearch search unavailable, falling back to MySQL", ex);
        }
        return searchFromMysql(page, size, keyword, categoryId, minPrice, maxPrice);
    }

    private ProductApiModels.ProductPageResponse searchFromMysql(long page, long size, String keyword,
                                                                   Long categoryId, java.math.BigDecimal minPrice,
                                                                   java.math.BigDecimal maxPrice) {
        LambdaQueryWrapper<ProductEntity> query = new LambdaQueryWrapper<>();
        query.eq(categoryId != null, ProductEntity::getCategoryId, categoryId)
                .eq(ProductEntity::getStatus, 1)
                .ge(minPrice != null, ProductEntity::getPrice, minPrice)
                .le(maxPrice != null, ProductEntity::getPrice, maxPrice)
                .and(keyword != null && !keyword.isBlank(), wrapper -> wrapper
                        .like(ProductEntity::getName, keyword.trim())
                        .or().like(ProductEntity::getSubtitle, keyword.trim()))
                .orderByDesc(ProductEntity::getCreatedAt);
        Page<ProductEntity> result = productMapper.selectPage(new Page<>(page, size), query);
        List<ProductApiModels.ProductResponse> items = result.getRecords().stream()
                .map(this::toResponse).toList();
        return new ProductApiModels.ProductPageResponse(items, result.getTotal(), page, size);
    }

    private ProductApiModels.ProductPageResponse searchFromElastic(long page, long size, String keyword,
                                                                    Long categoryId, java.math.BigDecimal minPrice,
                                                                    java.math.BigDecimal maxPrice) {
        ProductSearchService.SearchResult result = searchService.search(keyword, categoryId, minPrice, maxPrice, page, size);
        List<Long> ids = result.ids();
        List<ProductEntity> products = productMapper.selectBatchIds(ids).stream()
                .sorted(java.util.Comparator.comparing(ProductEntity::getCreatedAt).reversed()).toList();
        List<ProductApiModels.ProductResponse> items = products.stream()
                .map(this::toResponse).toList();
        return new ProductApiModels.ProductPageResponse(items, result.total(), page, size);
    }

    private void index(ProductEntity product) {
        try { searchService.index(new ProductDocument(product)); }
        catch (RuntimeException ex) { log.warn("failed to index product, id={}", product.getId(), ex); }
    }

    private void createDefaultSku(ProductEntity product, LocalDateTime now) {
        ProductSkuEntity sku = new ProductSkuEntity();
        sku.setId(IdGenerator.nextId()); sku.setProductId(product.getId());
        sku.setSkuCode("P" + product.getId() + "-DEFAULT"); sku.setName("默认规格"); sku.setSpecJson("{}");
        sku.setImageUrl(product.getCoverImageUrl()); sku.setPrice(product.getPrice()); sku.setMarketPrice(product.getMarketPrice());
        sku.setStatus(product.getStatus()); sku.setCreatedAt(now); sku.setUpdatedAt(now); skuMapper.insert(sku);
    }

    @Transactional
    public ProductApiModels.ProductResponse update(Long id, ProductApiModels.UpdateProductRequest request) {
        requireActiveCategory(request.categoryId());
        validateStatus(request.status());
        ProductEntity product = requireProduct(id);
        product.setCategoryId(request.categoryId());
        product.setName(request.name());
        product.setSubtitle(request.subtitle());
        product.setPrice(request.price());
        product.setStatus(request.status());
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.updateById(product);
        skuMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<ProductSkuEntity>lambdaUpdate()
                .eq(ProductSkuEntity::getProductId, id)
                .eq(ProductSkuEntity::getSkuCode, "P" + id + "-DEFAULT")
                .set(ProductSkuEntity::getPrice, product.getPrice())
                .set(ProductSkuEntity::getMarketPrice, product.getMarketPrice())
                .set(ProductSkuEntity::getImageUrl, product.getCoverImageUrl())
                .set(ProductSkuEntity::getStatus, product.getStatus())
                .set(ProductSkuEntity::getUpdatedAt, product.getUpdatedAt()));
        index(product);
        evict(id);
        return toResponse(product);
    }

    @Transactional
    public void delete(Long id) {
        requireProduct(id);
        productMapper.deleteById(id);
        try {
            searchService.delete(id);
        } catch (RuntimeException ex) {
            log.warn("failed to delete product index, id={}", id, ex);
        }
        evict(id);
    }

    private ProductEntity requireProduct(Long id) {
        ProductEntity product = productMapper.selectById(id);
        if (product == null) {
            throw new IllegalArgumentException("product not found: " + id);
        }
        return product;
    }

    private void requireActiveCategory(Long categoryId) {
        CategoryEntity category = categoryMapper.selectById(categoryId);
        if (category == null || !Integer.valueOf(1).equals(category.getStatus())) {
            throw new IllegalArgumentException("active category not found: " + categoryId);
        }
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("status must be 0 or 1");
        }
    }

    private ProductApiModels.ProductResponse toResponse(ProductEntity product) {
        return new ProductApiModels.ProductResponse(product.getId(), product.getCategoryId(), product.getName(),
                product.getSubtitle(), product.getPrice(), product.getStatus(), product.getCreatedAt(),
                product.getUpdatedAt());
    }

    private String cacheKey(Long id) {
        return CACHE_KEY_PREFIX + id;
    }

    private Duration cacheTtl() {
        return CACHE_TTL.plusSeconds(ThreadLocalRandom.current().nextLong(120));
    }

    private String toJson(ProductApiModels.ProductResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize product cache", ex);
        }
    }

    private ProductApiModels.ProductResponse fromCache(String cached) {
        try {
            return objectMapper.readValue(cached, ProductApiModels.ProductResponse.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to deserialize product cache", ex);
        }
    }

    private void evict(Long id) {
        try {
            redisTemplate.delete(cacheKey(id));
        } catch (RuntimeException ex) {
            log.warn("failed to evict product cache, id={}", id, ex);
        }
    }

    private String readCache(Long id) {
        try {
            return redisTemplate.opsForValue().get(cacheKey(id));
        } catch (RuntimeException ex) {
            log.warn("product cache unavailable, falling back to database, id={}", id, ex);
            return null;
        }
    }

    private void writeCache(Long id, ProductApiModels.ProductResponse response) {
        try {
            redisTemplate.opsForValue().set(cacheKey(id), toJson(response), cacheTtl());
        } catch (RuntimeException ex) {
            log.warn("failed to write product cache, id={}", id, ex);
        }
    }

    private void recordView(Long id) {
        try {
            redisTemplate.opsForZSet().incrementScore(HOT_KEY, String.valueOf(id), 1D);
        } catch (RuntimeException ex) {
            log.warn("failed to record product view, id={}", id, ex);
        }
    }
}
