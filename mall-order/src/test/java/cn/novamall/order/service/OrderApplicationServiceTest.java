package cn.novamall.order.service;

import cn.novamall.order.OrderApiModels;
import cn.novamall.order.client.InventoryClient;
import cn.novamall.order.client.ProductClient;
import cn.novamall.order.entity.OrderEntity;
import cn.novamall.order.entity.OrderItemEntity;
import cn.novamall.order.mapper.OrderItemMapper;
import cn.novamall.order.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private ProductClient productClient;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private OrderOutboxService orderOutboxService;

    @InjectMocks
    private OrderApplicationService service;

    @Test
    void createMergesDuplicateItemsAndAppendsOrderEvent() {
        ProductClient.ProductSnapshot product = product(100L, "Phone", "99.90", 1);
        when(productClient.get(100L)).thenReturn(product);
        when(inventoryClient.reserve(any(InventoryClient.ReserveRequest.class)))
                .thenReturn(new InventoryClient.ReserveResponse(true, "reserved"));
        when(orderMapper.insert(any(OrderEntity.class))).thenReturn(1);
        when(orderItemMapper.insert(any(OrderItemEntity.class))).thenReturn(1);

        OrderApiModels.OrderResponse response = service.create(7L, "alice",
                new OrderApiModels.CreateOrderRequest(List.of(
                        new OrderApiModels.OrderItemRequest(100L, 2),
                        new OrderApiModels.OrderItemRequest(100L, 3))));

        assertEquals("PENDING_PAYMENT", response.status());
        assertEquals(new BigDecimal("499.50"), response.totalAmount());
        assertEquals(1, response.items().size());
        assertEquals(5, response.items().get(0).quantity());
        verify(productClient).get(100L);
        ArgumentCaptor<InventoryClient.ReserveRequest> reserveCaptor =
                ArgumentCaptor.forClass(InventoryClient.ReserveRequest.class);
        verify(inventoryClient).reserve(reserveCaptor.capture());
        assertEquals(5, reserveCaptor.getValue().quantity());
        verify(orderOutboxService).appendOrderEvent(eq("ORDER_CREATED"), any(OrderEntity.class));
    }

    @Test
    void createReleasesPreviousReservationsWhenLaterReservationFails() {
        when(productClient.get(100L)).thenReturn(product(100L, "Phone", "10.00", 1));
        when(productClient.get(200L)).thenReturn(product(200L, "Case", "5.00", 1));
        when(inventoryClient.reserve(any(InventoryClient.ReserveRequest.class)))
                .thenReturn(new InventoryClient.ReserveResponse(true, "reserved"))
                .thenReturn(new InventoryClient.ReserveResponse(false, "insufficient stock"));

        assertThrows(IllegalArgumentException.class, () -> service.create(7L, "alice",
                new OrderApiModels.CreateOrderRequest(List.of(
                        new OrderApiModels.OrderItemRequest(100L, 1),
                        new OrderApiModels.OrderItemRequest(200L, 1)))));

        verify(inventoryClient).release(any(InventoryClient.ReleaseRequest.class));
        verify(orderMapper, never()).insert(any(OrderEntity.class));
        verify(orderOutboxService, never()).appendOrderEvent(any(String.class), any(OrderEntity.class));
    }

    @Test
    void payChangesPendingOrderToPaidAndAppendsEvent() {
        OrderEntity order = order(7L, "ORDER-1", "PENDING_PAYMENT");
        when(orderMapper.findOwnedForUpdate(7L, "ORDER-1")).thenReturn(order);
        when(orderMapper.markPaidIfPending("ORDER-1")).thenReturn(1);
        when(orderItemMapper.selectList(any())).thenReturn(List.of());

        OrderApiModels.OrderResponse response = service.pay(7L, "ORDER-1");

        assertEquals("PAID", response.status());
        verify(orderOutboxService).appendOrderEvent(eq("ORDER_PAID"), eq(order));
    }

    @Test
    void payRejectsAlreadyPaidOrder() {
        OrderEntity order = order(7L, "ORDER-1", "PAID");
        when(orderMapper.findOwnedForUpdate(7L, "ORDER-1")).thenReturn(order);

        assertThrows(IllegalArgumentException.class, () -> service.pay(7L, "ORDER-1"));
        verify(orderMapper, never()).markPaidIfPending(any(String.class));
        verify(orderOutboxService, never()).appendOrderEvent(any(String.class), any(OrderEntity.class));
    }

    @Test
    void lifecycleMovesPaidToShippedToCompleted() {
        OrderEntity paid = order(7L, "ORDER-1", "PAID");
        when(orderMapper.findOwnedForUpdate(7L, "ORDER-1")).thenReturn(paid);
        when(orderMapper.markShippedIfPaid("ORDER-1")).thenReturn(1);
        when(orderItemMapper.selectList(any())).thenReturn(List.of());

        OrderApiModels.OrderResponse shipped = service.ship(7L, "ORDER-1");

        assertEquals("SHIPPED", shipped.status());
        verify(orderOutboxService).appendOrderEvent(eq("ORDER_SHIPPED"), eq(paid));

        paid.setStatus("SHIPPED");
        when(orderMapper.markCompletedIfShipped("ORDER-1")).thenReturn(1);
        OrderApiModels.OrderResponse completed = service.complete(7L, "ORDER-1");

        assertEquals("COMPLETED", completed.status());
        verify(orderOutboxService).appendOrderEvent(eq("ORDER_COMPLETED"), eq(paid), eq(List.of()));
    }

    @Test
    void completeRejectsPendingPaymentOrder() {
        OrderEntity order = order(7L, "ORDER-1", "PENDING_PAYMENT");
        when(orderMapper.findOwnedForUpdate(7L, "ORDER-1")).thenReturn(order);

        assertThrows(IllegalArgumentException.class, () -> service.complete(7L, "ORDER-1"));
        verify(orderMapper, never()).markCompletedIfShipped(any(String.class));
    }

    private ProductClient.ProductSnapshot product(Long id, String name, String price, int status) {
        return new ProductClient.ProductSnapshot(id, name, new BigDecimal(price), status);
    }

    private OrderEntity order(Long userId, String orderNo, String status) {
        OrderEntity order = new OrderEntity();
        order.setId(1L);
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setUserName("alice");
        order.setTotalAmount(new BigDecimal("10.00"));
        order.setStatus(status);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }
}
