package cn.novamall.inventory.service;

import cn.novamall.common.id.IdGenerator;
import cn.novamall.inventory.InventoryApiModels;
import cn.novamall.inventory.SeckillApiModels;
import cn.novamall.inventory.config.SeckillMessagingConfig;
import cn.novamall.inventory.entity.SeckillOrderEntity;
import cn.novamall.inventory.mapper.SeckillOrderMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillApplicationService {
    private static final String STOCK_PREFIX = "seckill:stock:";
    private static final String USERS_PREFIX = "seckill:users:";
    private static final String SUCCESS = "SUCCESS";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String RESERVE_PREFIX = "SECKILL-";

    private static final String TAKE_STOCK_LUA = """
            local stock = redis.call('GET', KEYS[1])
            if not stock or tonumber(stock) <= 0 then return 0 end
            if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return -1 end
            redis.call('DECR', KEYS[1])
            redis.call('SADD', KEYS[2], ARGV[1])
            redis.call('EXPIRE', KEYS[2], ARGV[2])
            return 1
            """;
    private static final String RETURN_STOCK_LUA = """
            redis.call('INCR', KEYS[1])
            redis.call('SREM', KEYS[2], ARGV[1])
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final InventoryApplicationService inventoryService;
    private final SeckillOrderMapper orderMapper;

    public SeckillApplicationService(StringRedisTemplate redisTemplate, RabbitTemplate rabbitTemplate,
                                     ObjectMapper objectMapper, InventoryApplicationService inventoryService,
                                     SeckillOrderMapper orderMapper) {
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.inventoryService = inventoryService;
        this.orderMapper = orderMapper;
    }

    public SeckillApiModels.PrepareResponse prepare(Long productId, Integer stock) {
        InventoryApiModels.StockResponse current = inventoryService.get(productId);
        if (stock > current.availableStock()) {
            throw new IllegalArgumentException("seckill stock cannot exceed available stock");
        }
        redisTemplate.opsForValue().set(stockKey(productId), String.valueOf(stock));
        redisTemplate.delete(usersKey(productId));
        return new SeckillApiModels.PrepareResponse(productId, stock, stockKey(productId));
    }

    public SeckillApiModels.SubmitResponse submit(Long userId, Long productId) {
        Long result = redisTemplate.execute(RedisScript.of(TAKE_STOCK_LUA, Long.class),
                List.of(stockKey(productId), usersKey(productId)), String.valueOf(userId), "86400");
        if (result == null || result == 0) {
            throw new IllegalArgumentException("seckill is sold out or not prepared");
        }
        if (result == -1) {
            throw new IllegalArgumentException("user already participated");
        }
        String seckillNo = RESERVE_PREFIX + IdGenerator.nextId();
        String messageId = UUID.randomUUID().toString();
        SeckillEvent event = new SeckillEvent(messageId, seckillNo, userId, productId, 1);
        try {
            CorrelationData correlation = new CorrelationData(messageId);
            rabbitTemplate.convertAndSend(SeckillMessagingConfig.EVENTS_EXCHANGE,
                    "seckill.order", toJson(event), correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck()) {
                throw new IllegalStateException(confirm == null ? "publisher confirm missing" : confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                throw new IllegalStateException("seckill event was not routed");
            }
            return new SeckillApiModels.SubmitResponse(seckillNo, ACCEPTED, "seckill request accepted");
        } catch (Exception ex) {
            returnStock(productId, userId);
            throw new IllegalStateException("seckill is temporarily unavailable", ex);
        }
    }

    public SeckillApiModels.OrderResponse get(String seckillNo, Long userId) {
        SeckillOrderEntity order = orderMapper.findByNo(seckillNo);
        if (order == null || !userId.equals(order.getUserId())) {
            throw new IllegalArgumentException("seckill order not found: " + seckillNo);
        }
        return toResponse(order);
    }

    @Transactional
    public void consume(SeckillEvent event) {
        if (orderMapper.findByNo(event.seckillNo()) != null) return;
        if (orderMapper.findByUserProduct(event.userId(), event.productId()) != null) return;
        InventoryApiModels.ReserveResponse reservation = inventoryService.reserve(
                new InventoryApiModels.ReserveRequest(event.productId(), event.quantity(), event.seckillNo()));
        if (reservation == null || !reservation.success()) {
            returnStock(event.productId(), event.userId());
            throw new IllegalStateException("inventory reservation failed: "
                    + (reservation == null ? "empty response" : reservation.message()));
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            SeckillOrderEntity order = new SeckillOrderEntity();
            order.setId(IdGenerator.nextId()); order.setSeckillNo(event.seckillNo());
            order.setUserId(event.userId()); order.setProductId(event.productId());
            order.setQuantity(event.quantity()); order.setStatus(SUCCESS);
            order.setMessageId(event.messageId()); order.setCreatedAt(now); order.setUpdatedAt(now);
            orderMapper.insert(order);
        } catch (RuntimeException ex) {
            inventoryService.release(event.seckillNo());
            returnStock(event.productId(), event.userId());
            throw ex;
        }
    }

    private void returnStock(Long productId, Long userId) {
        redisTemplate.execute(RedisScript.of(RETURN_STOCK_LUA, Long.class),
                List.of(stockKey(productId), usersKey(productId)), String.valueOf(userId));
    }

    private SeckillApiModels.OrderResponse toResponse(SeckillOrderEntity order) {
        return new SeckillApiModels.OrderResponse(order.getSeckillNo(), order.getUserId(),
                order.getProductId(), order.getQuantity(), order.getStatus(), order.getCreatedAt());
    }

    private String toJson(SeckillEvent event) {
        try { return objectMapper.writeValueAsString(event); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("failed to serialize seckill event", ex); }
    }

    private String stockKey(Long productId) { return STOCK_PREFIX + productId; }
    private String usersKey(Long productId) { return USERS_PREFIX + productId; }

    public record SeckillEvent(String messageId, String seckillNo, Long userId,
                               Long productId, Integer quantity) { }
}
