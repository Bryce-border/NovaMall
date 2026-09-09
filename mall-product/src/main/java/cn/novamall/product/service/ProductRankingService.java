package cn.novamall.product.service;

import cn.novamall.product.ProductOrderEventListener.OrderItem;
import cn.novamall.product.ProductApiModels;
import cn.novamall.product.entity.ProductEntity;
import cn.novamall.product.mapper.ProductMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.List;

@Service
public class ProductRankingService {
    private static final String SALES_KEY = "product:sales";
    private static final String EVENT_KEY_PREFIX = "product:sales:event:";
    private final StringRedisTemplate redisTemplate;
    private final ProductMapper productMapper;

    public ProductRankingService(StringRedisTemplate redisTemplate, ProductMapper productMapper) {
        this.redisTemplate = redisTemplate;
        this.productMapper = productMapper;
    }

    public void recordCompletedOrder(String eventId, List<OrderItem> items) {
        if (items == null || items.stream().anyMatch(item -> item.productId() == null
                || item.quantity() == null || item.quantity() <= 0)) {
            throw new IllegalArgumentException("invalid order item in completion event");
        }
        Boolean firstDelivery = redisTemplate.opsForValue().setIfAbsent(
                EVENT_KEY_PREFIX + eventId, "1", Duration.ofDays(30));
        if (!Boolean.TRUE.equals(firstDelivery)) return;
        for (OrderItem item : items) {
            redisTemplate.opsForZSet().incrementScore(SALES_KEY,
                    String.valueOf(item.productId()), item.quantity());
        }
    }

    public List<ProductApiModels.SalesProductResponse> topSales(long limit) {
        var entries = redisTemplate.opsForZSet().reverseRangeWithScores(SALES_KEY, 0, limit - 1);
        if (entries == null || entries.isEmpty()) return List.of();
        List<Long> ids = entries.stream().map(e -> Long.valueOf(String.valueOf(e.getValue()))).toList();
        var products = productMapper.selectBatchIds(ids).stream()
                .filter(p -> Integer.valueOf(1).equals(p.getStatus()))
                .collect(java.util.stream.Collectors.toMap(ProductEntity::getId, p -> p));
        return ids.stream().filter(products::containsKey).map(id -> {
            ProductEntity p = products.get(id);
            Double score = entries.stream().filter(e -> String.valueOf(id).equals(String.valueOf(e.getValue())))
                    .map(org.springframework.data.redis.core.ZSetOperations.TypedTuple::getScore)
                    .findFirst().orElse(0D);
            return new ProductApiModels.SalesProductResponse(id, p.getName(), p.getPrice(), score);
        }).toList();
    }
}
