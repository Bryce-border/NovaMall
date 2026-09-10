package cn.novamall.user.service;

import cn.novamall.user.UserApiModels;
import cn.novamall.user.client.ProductClient;
import cn.novamall.user.entity.CartItemEntity;
import cn.novamall.user.mapper.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserCartServiceTest {
    @Mock UserMapper userMapper;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserAddressMapper addressMapper;
    @Mock CartItemMapper cartMapper;
    @Mock ProductClient productClient;
    @InjectMocks UserApplicationService service;

    @Test
    void addUsesServerProductAssociationAndAtomicQuantityUpdate() {
        when(productClient.get(101L)).thenReturn(new ProductClient.SkuSnapshot(101L, 100L, "BLACK", "Phone",
                "Black", Map.of(), null, new BigDecimal("10.00"), null, 1));
        when(cartMapper.selectOne(any())).thenReturn(item());
        service.addCart(7L, new UserApiModels.CartItemRequest(101L, 2));
        ArgumentCaptor<CartItemEntity> added = ArgumentCaptor.forClass(CartItemEntity.class);
        verify(cartMapper).addQuantity(added.capture());
        assertEquals(100L, added.getValue().getProductId());
        assertEquals(101L, added.getValue().getSkuId());
        assertEquals(7L, added.getValue().getUserId());
        verify(cartMapper, never()).updateById(any(CartItemEntity.class));
    }

    @Test
    void concurrentQuantityChangeIsReportedAsConflict() {
        when(cartMapper.selectOne(any())).thenReturn(item());
        when(cartMapper.updateVersioned(any())).thenReturn(0);
        assertThrows(IllegalStateException.class,
                () -> service.updateCart(7L, 1L, new UserApiModels.CartUpdateRequest(3)));
    }

    @Test
    void selectionChangeAlsoUsesVersionedUpdate() {
        when(cartMapper.selectOne(any())).thenReturn(item());
        when(cartMapper.updateVersioned(any())).thenReturn(1);
        assertFalse(service.selectCart(7L, 1L, false).selected());
        ArgumentCaptor<CartItemEntity> changed = ArgumentCaptor.forClass(CartItemEntity.class);
        verify(cartMapper).updateVersioned(changed.capture());
        assertEquals(4L, changed.getValue().getVersion());
        assertEquals(0, changed.getValue().getSelected());
    }

    @Test
    void nonPositiveQuantityDoesNotCallProductServiceOrDatabase() {
        assertThrows(IllegalArgumentException.class,
                () -> service.addCart(7L, new UserApiModels.CartItemRequest(101L, -1)));
        verifyNoInteractions(productClient, cartMapper);
    }

    private CartItemEntity item() {
        CartItemEntity item = new CartItemEntity();
        item.setId(1L);
        item.setUserId(7L);
        item.setProductId(100L);
        item.setSkuId(101L);
        item.setQuantity(2);
        item.setSelected(1);
        item.setVersion(4L);
        return item;
    }
}
