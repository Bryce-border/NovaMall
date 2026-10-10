package cn.novamall.order.service;

import cn.novamall.common.id.IdGenerator;
import cn.novamall.order.CartOrderApiModels.CheckoutRequest;
import cn.novamall.order.OrderApiModels;
import cn.novamall.order.client.UserClient;
import cn.novamall.order.mapper.CartCheckoutMapper;
import cn.novamall.order.mapper.CartCleanupMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CartOrderService {
    private final CartCheckoutMapper checkouts;
    private final CartCleanupMapper cleanup;
    private final UserClient users;
    private final OrderApplicationService orders;
    private final ObjectMapper json;
    private final CouponService coupons;

    public CartOrderService(CartCheckoutMapper checkouts, CartCleanupMapper cleanup, UserClient users,
                            OrderApplicationService orders, ObjectMapper json, CouponService coupons) {
        this.checkouts = checkouts;
        this.cleanup = cleanup;
        this.users = users;
        this.orders = orders;
        this.json = json;
        this.coupons = coupons;
    }

    @Transactional
    public OrderApiModels.OrderResponse checkout(Long userId, String userName, CheckoutRequest request) {
        if (request.requestKey() == null || !request.requestKey().matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                || request.addressId() == null || request.addressId() <= 0) {
            throw new IllegalArgumentException("UUID requestKey and positive addressId required");
        }
        String key = request.requestKey().toLowerCase(Locale.ROOT);
        // The unique key serializes retries, including requests arriving before the first commit.
        checkouts.claim(userId, key, request.addressId());
        CartCheckoutMapper.Checkout checkout = checkouts.findForUpdate(userId, key);
        if (checkout == null) throw new IllegalStateException("checkout claim missing");
        if (!request.addressId().equals(checkout.addressId())) {
            throw new IllegalArgumentException("requestKey already used with another address");
        }
        coupons.bindCheckout(userId, key, request.couponId());
        if (checkout.orderNo() != null) return orders.get(userId, checkout.orderNo());

        List<UserClient.CartItemSnapshot> items = users.selectedCart(userId);
        validateItems(items);
        List<OrderApiModels.OrderItemRequest> orderItems = items.stream()
                .map(item -> new OrderApiModels.OrderItemRequest(null, item.skuId(), item.quantity())).toList();
        OrderApiModels.OrderResponse order = orders.create(userId, userName,
                new OrderApiModels.CreateOrderRequest(orderItems, request.addressId(), request.couponId()));
        List<UserClient.CartCleanupItem> purchased = items.stream()
                .map(item -> new UserClient.CartCleanupItem(item.cartItemId(), item.version())).toList();
        try {
            cleanup.append(IdGenerator.nextId(), userId, order.orderNo(), json.writeValueAsString(purchased));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("cannot serialize cart cleanup", ex);
        }
        if (checkouts.complete(userId, key, order.orderNo()) != 1) {
            throw new IllegalStateException("checkout completion failed");
        }
        return order;
    }

    private void validateItems(List<UserClient.CartItemSnapshot> items) {
        if (items == null || items.isEmpty() || items.size() > 100) {
            throw new IllegalArgumentException("select 1 to 100 cart items before checkout");
        }
        Set<Long> ids = new HashSet<>();
        for (UserClient.CartItemSnapshot item : items) {
            if (item == null || item.cartItemId() == null || item.cartItemId() <= 0
                    || item.skuId() == null || item.skuId() <= 0 || item.quantity() == null || item.quantity() <= 0
                    || item.version() == null || item.version() < 0 || !ids.add(item.cartItemId())) {
                throw new IllegalArgumentException("invalid cart checkout snapshot");
            }
        }
    }
}
