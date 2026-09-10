package cn.novamall.order.service;

import cn.novamall.order.client.UserClient;
import cn.novamall.order.mapper.CartCleanupMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartCleanupWorkerTest {
    @Mock CartCleanupMapper cleanup;
    @Mock UserClient users;
    @Spy ObjectMapper json = new ObjectMapper();
    @InjectMocks CartCleanupWorker worker;

    @Test
    void successfulDeliveryAcknowledgesOnlyAfterUserServiceReturns() {
        when(cleanup.pending()).thenReturn(List.of(task()));
        worker.processPending();
        InOrder order = inOrder(users, cleanup);
        ArgumentCaptor<UserClient.CartCleanupRequest> request = ArgumentCaptor.forClass(UserClient.CartCleanupRequest.class);
        order.verify(users).cleanupCart(request.capture());
        order.verify(cleanup).done(1L);
        assertEquals(7L, request.getValue().userId());
        assertEquals(4L, request.getValue().items().get(0).version());
        verify(cleanup, never()).retry(any(), anyLong());
    }

    @Test
    void remoteFailureSchedulesRetryWithoutDeletingTask() {
        when(cleanup.pending()).thenReturn(List.of(task()));
        doThrow(new IllegalStateException("timeout")).when(users).cleanupCart(any());
        worker.processPending();
        verify(cleanup).retry(1L, 2L);
        verify(cleanup, never()).done(any());
    }

    @Test
    void failedAcknowledgmentCanRedeliverIdempotentCleanup() {
        when(cleanup.pending()).thenReturn(List.of(task()));
        when(cleanup.done(1L)).thenThrow(new IllegalStateException("database unavailable"));
        worker.processPending();
        verify(users).cleanupCart(any());
        verify(cleanup).retry(1L, 2L);
    }

    private CartCleanupMapper.Task task() {
        return new CartCleanupMapper.Task(1L, 7L, "[{\"cartItemId\":9,\"version\":4}]", 0);
    }
}
