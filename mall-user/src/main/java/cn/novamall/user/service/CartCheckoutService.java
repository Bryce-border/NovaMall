package cn.novamall.user.service;

import cn.novamall.user.CartCheckoutModels;
import cn.novamall.user.entity.CartItemEntity;
import cn.novamall.user.mapper.CartItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CartCheckoutService {
    private final CartItemMapper cart;

    public CartCheckoutService(CartItemMapper cart) { this.cart = cart; }

    public List<CartCheckoutModels.Item> selected(Long userId) {
        List<CartItemEntity> items = cart.selected(userId);
        if (items.size() > 100) throw new IllegalArgumentException("checkout supports at most 100 cart items");
        return items.stream().map(item -> new CartCheckoutModels.Item(item.getId(), item.getSkuId(),
                item.getQuantity(), item.getVersion())).toList();
    }

    @Transactional
    public void cleanup(CartCheckoutModels.CleanupRequest request) {
        for (CartCheckoutModels.CleanupItem item : request.items()) {
            // A later edit changes the version, so delayed or repeated cleanup leaves it intact.
            cart.deletePurchased(request.userId(), item.cartItemId(), item.version());
        }
    }
}
