package cn.novamall.inventory.service;

import cn.novamall.inventory.InventoryApiModels;
import cn.novamall.inventory.entity.InventoryEntity;
import cn.novamall.inventory.entity.InventoryReservationEntity;
import cn.novamall.inventory.mapper.InventoryMapper;
import cn.novamall.inventory.mapper.InventoryReservationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryApplicationServiceTest {

    @Mock
    private InventoryMapper inventoryMapper;

    @Mock
    private InventoryReservationMapper reservationMapper;

    @InjectMocks
    private InventoryApplicationService service;

    @Test
    void reserveRejectsInsufficientStockWithoutWritingReservation() {
        InventoryEntity inventory = inventory(8, 2, 0, 3);
        when(inventoryMapper.selectList(any())).thenReturn(List.of(inventory));
        when(reservationMapper.findByOrderAndProduct("ORDER-1", 100L)).thenReturn(null);

        InventoryApiModels.ReserveResponse response = service.reserve(
                new InventoryApiModels.ReserveRequest(100L, 3, "ORDER-1"));

        assertFalse(response.success());
        assertEquals("insufficient stock", response.message());
        verify(inventoryMapper, never()).reserve(anyLong(), anyInt(), anyInt());
        verify(reservationMapper, never()).insert(any(InventoryReservationEntity.class));
    }

    @Test
    void reserveIsIdempotentForExistingLockedReservation() {
        InventoryReservationEntity existing = reservation(7L, "LOCKED", 100L, 2);
        when(reservationMapper.findByOrderAndProduct("ORDER-1", 100L)).thenReturn(existing);

        InventoryApiModels.ReserveResponse response = service.reserve(
                new InventoryApiModels.ReserveRequest(100L, 2, "ORDER-1"));

        assertTrue(response.success());
        assertEquals("already reserved", response.message());
        verify(inventoryMapper, never()).selectOne(any());
        verify(reservationMapper, never()).insert(any(InventoryReservationEntity.class));
    }

    @Test
    void reserveUsesOptimisticLockAndCreatesReservationAfterSuccessfulUpdate() {
        InventoryEntity inventory = inventory(8, 8, 0, 3);
        when(reservationMapper.findByOrderAndProduct("ORDER-1", 100L)).thenReturn(null);
        when(inventoryMapper.selectList(any())).thenReturn(List.of(inventory));
        when(inventoryMapper.reserve(100L, 2, 3)).thenReturn(1);

        InventoryApiModels.ReserveResponse response = service.reserve(
                new InventoryApiModels.ReserveRequest(100L, 2, "ORDER-1"));

        assertTrue(response.success());
        assertEquals("reserved", response.message());
        ArgumentCaptor<InventoryReservationEntity> captor = ArgumentCaptor.forClass(InventoryReservationEntity.class);
        verify(reservationMapper).insert(captor.capture());
        assertEquals("ORDER-1", captor.getValue().getOrderNo());
        assertEquals(100L, captor.getValue().getProductId());
        assertEquals(2, captor.getValue().getQuantity());
        assertEquals("LOCKED", captor.getValue().getStatus());
    }

    @Test
    void releaseOnlyChangesLockedReservationsAndIsIdempotent() {
        InventoryReservationEntity locked = reservation(7L, "LOCKED", 100L, 2);
        when(reservationMapper.findLockedByOrderNo("ORDER-1")).thenReturn(List.of(locked));
        when(reservationMapper.markReleased(7L)).thenReturn(1);
        when(inventoryMapper.release(100L, 2)).thenReturn(1);

        service.release("ORDER-1");

        verify(reservationMapper).markReleased(7L);
        verify(inventoryMapper).release(100L, 2);
    }

    @Test
    void confirmRejectsMissingReservations() {
        when(reservationMapper.findByOrderForUpdate("ORDER-1")).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> service.confirm("ORDER-1"));
        verify(inventoryMapper, never()).confirm(anyLong(), anyInt());
    }

    @Test
    void confirmIsIdempotentForConfirmedReservation() {
        InventoryReservationEntity confirmed = reservation(7L, "CONFIRMED", 100L, 2);
        when(reservationMapper.findByOrderForUpdate("ORDER-1")).thenReturn(List.of(confirmed));

        service.confirm("ORDER-1");

        verify(reservationMapper, never()).markConfirmed(anyLong());
        verify(inventoryMapper, never()).confirm(anyLong(), anyInt());
    }

    private InventoryEntity inventory(int total, int available, int locked, int version) {
        InventoryEntity entity = new InventoryEntity();
        entity.setProductId(100L);
        entity.setTotalStock(total);
        entity.setAvailableStock(available);
        entity.setLockedStock(locked);
        entity.setVersion(version);
        return entity;
    }

    @Test
    void skuReservationRecordsExactSkuAndUsesItsVersion() {
        InventoryEntity row = inventory(8, 8, 0, 4);
        row.setSkuId(101L);
        when(inventoryMapper.selectOne(any())).thenReturn(row);
        when(inventoryMapper.reserveSku(101L, 2, 4)).thenReturn(1);
        assertTrue(service.reserve(new InventoryApiModels.ReserveRequest(100L, 101L, 2, "ORDER-1")).success());
        ArgumentCaptor<InventoryReservationEntity> saved = ArgumentCaptor.forClass(InventoryReservationEntity.class);
        verify(reservationMapper).insert(saved.capture());
        assertEquals(101L, saved.getValue().getSkuId());
        verify(inventoryMapper, never()).reserve(anyLong(), anyInt(), anyInt());
    }

    @Test
    void rejectsSkuOfAnotherProductBeforeDeductingStock() {
        InventoryEntity row = inventory(8, 8, 0, 4);
        row.setSkuId(101L);
        when(inventoryMapper.selectOne(any())).thenReturn(row);
        assertThrows(IllegalArgumentException.class, () -> service.reserve(
                new InventoryApiModels.ReserveRequest(200L, 101L, 2, "ORDER-1")));
        verify(inventoryMapper, never()).reserveSku(anyLong(), anyInt(), anyInt());
    }

    @Test
    void repeatedReservationCannotChangeQuantity() {
        InventoryReservationEntity existing = reservation(1L, "LOCKED", 100L, 2);
        existing.setSkuId(101L);
        when(reservationMapper.findByOrderAndSku("ORDER-1", 101L)).thenReturn(existing);
        assertFalse(service.reserve(new InventoryApiModels.ReserveRequest(100L, 101L, 3, "ORDER-1")).success());
        verify(inventoryMapper, never()).reserveSku(anyLong(), anyInt(), anyInt());
    }

    @Test
    void ambiguousProductStockRequiresSku() {
        InventoryEntity first = inventory(8, 8, 0, 0);
        first.setSkuId(101L);
        InventoryEntity second = inventory(9, 9, 0, 0);
        second.setSkuId(102L);
        when(inventoryMapper.selectList(any())).thenReturn(List.of(first, second));
        assertThrows(IllegalArgumentException.class, () -> service.get(100L));
        assertThrows(IllegalArgumentException.class, () -> service.addStock(100L, 2));
        verify(inventoryMapper, never()).increase(anyLong(), anyInt());
    }

    @Test
    void skuRestockReloadsTheSameSku() {
        InventoryEntity row = inventory(8, 8, 0, 0);
        row.setSkuId(102L);
        when(inventoryMapper.selectOne(any())).thenReturn(row);
        when(inventoryMapper.increaseSku(102L, 2)).thenReturn(1);
        assertEquals(102L, service.addStock(100L, 102L, 2).skuId());
        verify(inventoryMapper, never()).selectList(any());
        verify(inventoryMapper, never()).increase(anyLong(), anyInt());
    }

    @Test
    void releasesTwoSkusOfSameProductIndependently() {
        InventoryReservationEntity first = reservation(1L, "LOCKED", 100L, 2);
        first.setSkuId(101L);
        InventoryReservationEntity second = reservation(2L, "LOCKED", 100L, 3);
        second.setSkuId(102L);
        when(reservationMapper.findLockedByOrderNo("ORDER-1")).thenReturn(List.of(first, second));
        when(reservationMapper.markReleased(1L)).thenReturn(1);
        when(reservationMapper.markReleased(2L)).thenReturn(1);
        when(inventoryMapper.releaseSku(101L, 2)).thenReturn(1);
        when(inventoryMapper.releaseSku(102L, 3)).thenReturn(1);
        service.release("ORDER-1");
        verify(inventoryMapper).releaseSku(101L, 2);
        verify(inventoryMapper).releaseSku(102L, 3);
        verify(inventoryMapper, never()).release(anyLong(), anyInt());
    }

    @Test
    void confirmsSkuAndDoesNotTouchOtherVariants() {
        InventoryReservationEntity row = reservation(1L, "LOCKED", 100L, 2);
        row.setSkuId(101L);
        when(reservationMapper.findByOrderForUpdate("ORDER-1")).thenReturn(List.of(row));
        when(reservationMapper.markConfirmed(1L)).thenReturn(1);
        when(inventoryMapper.confirmSku(101L, 2)).thenReturn(1);
        service.confirm("ORDER-1");
        verify(inventoryMapper).confirmSku(101L, 2);
        verify(inventoryMapper, never()).confirm(anyLong(), anyInt());
    }

    @Test
    void invalidQuantityNeverReachesDatabase() {
        assertThrows(IllegalArgumentException.class, () -> service.reserve(
                new InventoryApiModels.ReserveRequest(100L, 101L, -1, "ORDER-1")));
        verify(inventoryMapper, never()).selectOne(any());
    }

    private InventoryReservationEntity reservation(Long id, String status, Long productId, int quantity) {
        InventoryReservationEntity entity = new InventoryReservationEntity();
        entity.setId(id);
        entity.setOrderNo("ORDER-1");
        entity.setStatus(status);
        entity.setProductId(productId);
        entity.setQuantity(quantity);
        return entity;
    }
}
