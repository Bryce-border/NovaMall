package cn.novamall.order.service;

import cn.novamall.order.OrderApiModels;
import cn.novamall.order.client.InventoryClient;
import cn.novamall.order.client.ProductClient;
import cn.novamall.order.client.UserClient;
import cn.novamall.order.mapper.OrderAddressMapper;
import cn.novamall.order.entity.OrderAddressEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.novamall.order.entity.OrderEntity;
import cn.novamall.order.entity.OrderItemEntity;
import cn.novamall.order.mapper.OrderItemMapper;
import cn.novamall.order.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

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

    @Mock private OrderRecordService orderRecords;

    @Mock private UserClient userClient;
    @Mock private OrderAddressMapper orderAddressMapper;
    @Spy private ObjectMapper json = new ObjectMapper();

    @InjectMocks
    private OrderApplicationService service;

    @Test
    void createMergesDuplicateItemsAndAppendsOrderEvent() {
        ProductClient.SkuSnapshot product = product(100L, "Phone", "99.90", 1);
        when(productClient.defaultSku(100L)).thenReturn(product);
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
        verify(productClient).defaultSku(100L);
        ArgumentCaptor<InventoryClient.ReserveRequest> reserveCaptor =
                ArgumentCaptor.forClass(InventoryClient.ReserveRequest.class);
        verify(inventoryClient).reserve(reserveCaptor.capture());
        assertEquals(5, reserveCaptor.getValue().quantity());
        verify(orderOutboxService).appendOrderEvent(eq("ORDER_CREATED"), any(OrderEntity.class));
    }

    @Test
    void createReleasesPreviousReservationsWhenLaterReservationFails() {
        when(productClient.defaultSku(100L)).thenReturn(product(100L, "Phone", "10.00", 1));
        when(productClient.defaultSku(200L)).thenReturn(product(200L, "Case", "5.00", 1));
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

    @Test
    void explicitSkusOfSameProductKeepSeparatePricesAndSnapshots() {
        when(productClient.sku(101L)).thenReturn(sku(101L, 100L, "Black", "10.00", 1));
        when(productClient.sku(102L)).thenReturn(sku(102L, 100L, "White", "12.00", 1));
        when(inventoryClient.reserve(any())).thenReturn(new InventoryClient.ReserveResponse(true, "reserved"));
        OrderApiModels.OrderResponse response = service.create(7L, "alice", new OrderApiModels.CreateOrderRequest(List.of(
                new OrderApiModels.OrderItemRequest(null, 101L, 2),
                new OrderApiModels.OrderItemRequest(null, 102L, 1))));

        assertEquals(new BigDecimal("32.00"), response.totalAmount());
        assertEquals(List.of(101L, 102L), response.items().stream().map(OrderApiModels.OrderItemResponse::skuId).toList());
        ArgumentCaptor<OrderItemEntity> saved = ArgumentCaptor.forClass(OrderItemEntity.class);
        verify(orderItemMapper, times(2)).insert(saved.capture());
        assertEquals("{\"color\":\"Black\"}", saved.getAllValues().get(0).getSpecJson());
        assertEquals("/White.jpg", saved.getAllValues().get(1).getCoverImageUrl());
        ArgumentCaptor<InventoryClient.ReserveRequest> reservations = ArgumentCaptor.forClass(InventoryClient.ReserveRequest.class);
        verify(inventoryClient, times(2)).reserve(reservations.capture());
        assertEquals(List.of(101L, 102L), reservations.getAllValues().stream().map(InventoryClient.ReserveRequest::skuId).toList());
    }

    @Test
    void mixedLegacyAndSkuReferencesMergeBeforeReservation() {
        when(productClient.defaultSku(100L)).thenReturn(product(100L, "Phone", "10.00", 1));
        when(inventoryClient.reserve(any())).thenReturn(new InventoryClient.ReserveResponse(true, "reserved"));
        OrderApiModels.OrderResponse response = service.create(7L, "alice", new OrderApiModels.CreateOrderRequest(List.of(
                new OrderApiModels.OrderItemRequest(100L, 2),
                new OrderApiModels.OrderItemRequest(null, 101L, 3))));
        assertEquals(1, response.items().size());
        assertEquals(5, response.items().get(0).quantity());
        verify(productClient, never()).sku(any());
        verify(inventoryClient).reserve(any());
    }

    @Test
    void rejectsAmbiguousProductAssociationBeforeRemoteCalls() {
        assertThrows(IllegalArgumentException.class, () -> service.create(7L, "alice",
                new OrderApiModels.CreateOrderRequest(List.of(new OrderApiModels.OrderItemRequest(999L, 101L, 1)))));
        verifyNoInteractions(productClient, inventoryClient, orderMapper);
    }

    @Test
    void unavailableSkuRejectsWholeOrderBeforeAnyReservation() {
        when(productClient.sku(101L)).thenReturn(sku(101L, 100L, "Black", "10.00", 1));
        when(productClient.sku(102L)).thenReturn(sku(102L, 100L, "White", "12.00", 0));
        assertThrows(IllegalArgumentException.class, () -> service.create(7L, "alice",
                new OrderApiModels.CreateOrderRequest(List.of(new OrderApiModels.OrderItemRequest(null, 101L, 1),
                        new OrderApiModels.OrderItemRequest(null, 102L, 1)))));
        verifyNoInteractions(inventoryClient, orderMapper);
    }

    @Test
    void foreignAddressRejectsBeforeReservingStock() {
        when(userClient.address(8L, 7L)).thenReturn(new UserClient.AddressSnapshot(8L, 9L,
                "Bob", "123", "P", "C", "D", "Road", null));
        assertThrows(IllegalArgumentException.class, () -> service.create(7L, "alice",
                new OrderApiModels.CreateOrderRequest(List.of(new OrderApiModels.OrderItemRequest(100L, 1)), 8L)));
        verifyNoInteractions(productClient, inventoryClient, orderMapper, orderAddressMapper);
    }

    @Test
    void ownedAddressIsCopiedIntoOrderSnapshot() {
        when(userClient.address(8L, 7L)).thenReturn(new UserClient.AddressSnapshot(8L, 7L,
                "Alice", "13800000000", "P", "C", "D", "Road 1", "123456"));
        when(productClient.defaultSku(100L)).thenReturn(product(100L, "Phone", "10.00", 1));
        when(inventoryClient.reserve(any())).thenReturn(new InventoryClient.ReserveResponse(true, "reserved"));
        OrderApiModels.OrderResponse order = service.create(7L, "alice",
                new OrderApiModels.CreateOrderRequest(List.of(new OrderApiModels.OrderItemRequest(100L, 1)), 8L));
        ArgumentCaptor<OrderAddressEntity> snapshot = ArgumentCaptor.forClass(OrderAddressEntity.class);
        verify(orderAddressMapper).insert(snapshot.capture());
        assertEquals(order.id(), snapshot.getValue().getOrderId());
        assertEquals("Road 1", snapshot.getValue().getDetailAddress());
        assertEquals("13800000000", snapshot.getValue().getReceiverPhone());
    }

    @Test
    void firstReservationTimeoutAlsoAttemptsCompensation() {
        when(productClient.defaultSku(100L)).thenReturn(product(100L, "Phone", "10.00", 1));
        when(inventoryClient.reserve(any())).thenThrow(new IllegalStateException("timeout"));
        assertThrows(IllegalStateException.class, () -> service.create(7L, "alice",
                new OrderApiModels.CreateOrderRequest(List.of(new OrderApiModels.OrderItemRequest(100L, 1)))));
        verify(inventoryClient).release(any());
    }

    @Test
    void mergedQuantityOverflowRejectsBeforeStockReservation() {
        when(productClient.defaultSku(100L)).thenReturn(product(100L, "Phone", "1.00", 1));
        assertThrows(IllegalArgumentException.class, () -> service.create(7L, "alice",
                new OrderApiModels.CreateOrderRequest(List.of(new OrderApiModels.OrderItemRequest(100L, Integer.MAX_VALUE),
                        new OrderApiModels.OrderItemRequest(null, 101L, 1)))));
        verifyNoInteractions(inventoryClient, orderMapper);
    }

    @Test
    void transactionRollbackAfterCreateReturnsStillCompensatesStock() {
        when(productClient.defaultSku(100L)).thenReturn(product(100L, "Phone", "10.00", 1));
        when(inventoryClient.reserve(any())).thenReturn(new InventoryClient.ReserveResponse(true, "reserved"));
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.create(7L, "alice", new OrderApiModels.CreateOrderRequest(
                    List.of(new OrderApiModels.OrderItemRequest(100L, 1))));
            verify(inventoryClient, never()).release(any());
            for (var callback : org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                callback.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(inventoryClient).release(any());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private ProductClient.SkuSnapshot product(Long id, String name, String price, int status) {
        return sku(id + 1, id, name, price, status);
    }

    private ProductClient.SkuSnapshot sku(Long id, Long productId, String name, String price, int status) {
        return new ProductClient.SkuSnapshot(id, productId, "SKU-" + id, "Phone", name,
                Map.of("color", name), "/" + name + ".jpg", new BigDecimal(price), null, status);
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
