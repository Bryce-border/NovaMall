package cn.novamall.inventory.service;

import cn.novamall.inventory.InventoryApiModels;
import cn.novamall.inventory.SeckillApiModels;
import cn.novamall.inventory.entity.SeckillOrderEntity;
import cn.novamall.inventory.mapper.SeckillOrderMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillApplicationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private InventoryApplicationService inventoryService;

    @Mock
    private SeckillOrderMapper orderMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SeckillApplicationService service;

    @Test
    void prepareRejectsStockAboveAvailableAmount() {
        when(inventoryService.get(100L)).thenReturn(new InventoryApiModels.StockResponse(
                100L, 10, 2, 0, 1));

        assertThrows(IllegalArgumentException.class, () -> service.prepare(100L, 3));
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void prepareInitializesRedisStockAndClearsParticipants() {
        when(inventoryService.get(100L)).thenReturn(new InventoryApiModels.StockResponse(
                100L, 10, 8, 0, 1));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        SeckillApiModels.PrepareResponse response = service.prepare(100L, 5);

        assertEquals(5, response.stock());
        assertEquals("seckill:stock:100", response.stockKey());
        verify(valueOperations).set("seckill:stock:100", "5");
        verify(redisTemplate).delete("seckill:users:100");
    }

    @Test
    void submitRejectsSoldOutRequestBeforePublishingMessage() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(0L);

        assertThrows(IllegalArgumentException.class, () -> service.submit(7L, 100L));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), anyString(),
                any(org.springframework.amqp.rabbit.connection.CorrelationData.class));
    }

    @Test
    void submitRejectsDuplicateParticipant() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(-1L);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.submit(7L, 100L));

        assertEquals("user already participated", exception.getMessage());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), anyString(),
                any(org.springframework.amqp.rabbit.connection.CorrelationData.class));
    }

    @Test
    void consumeIsIdempotentForExistingSeckillOrder() {
        SeckillOrderEntity existing = new SeckillOrderEntity();
        existing.setSeckillNo("SECKILL-1");
        when(orderMapper.findByNo("SECKILL-1")).thenReturn(existing);

        service.consume(new SeckillApplicationService.SeckillEvent("MSG-1", "SECKILL-1", 7L, 100L, 1));

        verify(inventoryService, never()).reserve(any());
        verify(orderMapper, never()).insert(any(SeckillOrderEntity.class));
    }

    @Test
    void consumeReleasesRedisReservationWhenDatabaseReservationFails() {
        when(orderMapper.findByNo("SECKILL-1")).thenReturn(null);
        when(orderMapper.findByUserProduct(7L, 100L)).thenReturn(null);
        when(inventoryService.reserve(any(InventoryApiModels.ReserveRequest.class)))
                .thenReturn(new InventoryApiModels.ReserveResponse(false, "insufficient stock"));

        assertThrows(IllegalStateException.class, () -> service.consume(
                new SeckillApplicationService.SeckillEvent("MSG-1", "SECKILL-1", 7L, 100L, 1)));

        verify(inventoryService, never()).release(any());
        verify(orderMapper, never()).insert(any(SeckillOrderEntity.class));
        verify(redisTemplate).execute(any(RedisScript.class), eq(java.util.List.of(
                "seckill:stock:100", "seckill:users:100")), eq("7"));
    }

    @Test
    void consumeReservesInventoryAndPersistsSuccessfulOrder() {
        when(orderMapper.findByNo("SECKILL-1")).thenReturn(null);
        when(orderMapper.findByUserProduct(7L, 100L)).thenReturn(null);
        when(inventoryService.reserve(any(InventoryApiModels.ReserveRequest.class)))
                .thenReturn(new InventoryApiModels.ReserveResponse(true, "reserved"));
        when(orderMapper.insert(any(SeckillOrderEntity.class))).thenReturn(1);

        service.consume(new SeckillApplicationService.SeckillEvent("MSG-1", "SECKILL-1", 7L, 100L, 1));

        ArgumentCaptor<SeckillOrderEntity> captor = ArgumentCaptor.forClass(SeckillOrderEntity.class);
        verify(orderMapper).insert(captor.capture());
        assertEquals("SECKILL-1", captor.getValue().getSeckillNo());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(100L, captor.getValue().getProductId());
        assertEquals("SUCCESS", captor.getValue().getStatus());
        verify(inventoryService).reserve(any(InventoryApiModels.ReserveRequest.class));
    }
}
