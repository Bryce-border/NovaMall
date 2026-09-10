package cn.novamall.user.service;

import cn.novamall.user.CartCheckoutModels;
import cn.novamall.user.entity.CartItemEntity;
import cn.novamall.user.mapper.CartItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.stream.LongStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartCheckoutServiceTest {
    @Mock CartItemMapper cart;
    @InjectMocks CartCheckoutService service;

    @Test
    void selectedSnapshotCarriesServerQuantityAndVersion() {
        when(cart.selected(7L)).thenReturn(List.of(item(1L)));
        CartCheckoutModels.Item snapshot = service.selected(7L).get(0);
        assertEquals(101L, snapshot.skuId());
        assertEquals(2, snapshot.quantity());
        assertEquals(4L, snapshot.version());
        verify(cart).selected(7L);
    }

    @Test
    void tooManySelectedRowsAreRejectedRatherThanSilentlyTruncated() {
        when(cart.selected(7L)).thenReturn(LongStream.rangeClosed(1, 101).mapToObj(this::item).toList());
        assertThrows(IllegalArgumentException.class, () -> service.selected(7L));
    }

    @Test
    void cleanupPassesOwnerAndOriginalVersionAndToleratesRedelivery() {
        CartCheckoutModels.CleanupRequest request = new CartCheckoutModels.CleanupRequest(7L,
                List.of(new CartCheckoutModels.CleanupItem(1L, 4L)));
        when(cart.deletePurchased(7L, 1L, 4L)).thenReturn(1, 0);
        service.cleanup(request);
        service.cleanup(request);
        verify(cart, times(2)).deletePurchased(7L, 1L, 4L);
        verifyNoMoreInteractions(cart);
    }

    private CartItemEntity item(Long id) {
        CartItemEntity item = new CartItemEntity();
        item.setId(id);
        item.setUserId(7L);
        item.setSkuId(101L);
        item.setQuantity(2);
        item.setVersion(4L);
        return item;
    }
}
