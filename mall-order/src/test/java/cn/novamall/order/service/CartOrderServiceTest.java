package cn.novamall.order.service;

import cn.novamall.order.CartOrderApiModels.CheckoutRequest;
import cn.novamall.order.OrderApiModels;
import cn.novamall.order.client.UserClient;
import cn.novamall.order.mapper.CartCheckoutMapper;
import cn.novamall.order.mapper.CartCleanupMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartOrderServiceTest {
    private static final String KEY = "e8b6a56a-d2bc-40a5-89b4-89ac1648a900";
    @Mock CartCheckoutMapper checkouts;
    @Mock CartCleanupMapper cleanup;
    @Mock UserClient users;
    @Mock OrderApplicationService orders;
    @Spy ObjectMapper json = new ObjectMapper();
    @InjectMocks CartOrderService service;

    @Test
    void checkoutUsesServerCartAndPersistsVersionedCleanup() throws Exception {
        claim(null);
        when(users.selectedCart(7L)).thenReturn(List.of(new UserClient.CartItemSnapshot(1L, 101L, 2, 4L)));
        when(orders.create(eq(7L), eq("alice"), any())).thenReturn(order());
        when(checkouts.complete(7L, KEY, "ORDER-1")).thenReturn(1);

        assertEquals("ORDER-1", service.checkout(7L, "alice", new CheckoutRequest(KEY, 8L)).orderNo());

        ArgumentCaptor<OrderApiModels.CreateOrderRequest> request = ArgumentCaptor.forClass(OrderApiModels.CreateOrderRequest.class);
        verify(orders).create(eq(7L), eq("alice"), request.capture());
        assertEquals(8L, request.getValue().addressId());
        assertNull(request.getValue().items().get(0).productId());
        assertEquals(101L, request.getValue().items().get(0).skuId());
        assertEquals(2, request.getValue().items().get(0).quantity());
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(cleanup).append(anyLong(), eq(7L), eq("ORDER-1"), payload.capture());
        assertEquals(4L, json.readTree(payload.getValue()).get(0).get("version").asLong());
        verify(users, never()).cleanupCart(any());
    }

    @Test
    void retryReturnsOriginalOrderWithoutReadingChangedCart() {
        claim("ORDER-1");
        when(orders.get(7L, "ORDER-1")).thenReturn(order());
        assertEquals("ORDER-1", service.checkout(7L, "alice", new CheckoutRequest(KEY.toUpperCase(), 8L)).orderNo());
        verifyNoInteractions(users, cleanup);
        verify(orders, never()).create(any(), any(), any());
    }

    @Test
    void sameKeyWithDifferentAddressIsRejected() {
        claim("ORDER-1");
        assertThrows(IllegalArgumentException.class,
                () -> service.checkout(7L, "alice", new CheckoutRequest(KEY, 9L)));
        verifyNoInteractions(users, cleanup, orders);
    }

    @Test
    void emptySelectedCartCreatesNoOrderOrCleanup() {
        claim(null);
        when(users.selectedCart(7L)).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> service.checkout(7L, "alice", new CheckoutRequest(KEY, 8L)));
        verifyNoInteractions(orders, cleanup);
        verify(checkouts, never()).complete(any(), any(), any());
    }

    @Test
    void failedOrderLeavesNoCleanupTask() {
        claim(null);
        when(users.selectedCart(7L)).thenReturn(List.of(new UserClient.CartItemSnapshot(1L, 101L, 2, 4L)));
        when(orders.create(any(), any(), any())).thenThrow(new IllegalArgumentException("stock unavailable"));
        assertThrows(IllegalArgumentException.class,
                () -> service.checkout(7L, "alice", new CheckoutRequest(KEY, 8L)));
        verifyNoInteractions(cleanup);
        verify(checkouts, never()).complete(any(), any(), any());
    }

    @Test
    void missingCartVersionPreventsUnsafeCleanup() {
        claim(null);
        when(users.selectedCart(7L)).thenReturn(List.of(new UserClient.CartItemSnapshot(1L, 101L, 2, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.checkout(7L, "alice", new CheckoutRequest(KEY, 8L)));
        verifyNoInteractions(orders, cleanup);
    }

    @Test
    void missingAddressIsRejectedBeforeClaim() {
        assertThrows(IllegalArgumentException.class,
                () -> service.checkout(7L, "alice", new CheckoutRequest(KEY, null)));
        verifyNoInteractions(checkouts, users, orders, cleanup);
    }

    private void claim(String orderNo) {
        when(checkouts.findForUpdate(7L, KEY)).thenReturn(new CartCheckoutMapper.Checkout(7L, KEY, 8L, orderNo));
    }

    private OrderApiModels.OrderResponse order() {
        return new OrderApiModels.OrderResponse(1L, "ORDER-1", 7L, new BigDecimal("20.00"),
                "PENDING_PAYMENT", List.of(), LocalDateTime.now());
    }
}
