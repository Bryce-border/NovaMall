package cn.novamall.order.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RefundWorkerTest {
    private final RefundJobs jobs=mock(RefundJobs.class);
    private final RefundChannel channel=mock(RefundChannel.class);
    private final RefundJobs.Job job=new RefundJobs.Job("RF1","O1","P1",new BigDecimal("10.00"),"lease",1,false);

    @Test void uncertainTransportDoesNotResubmitOrInventFailure() {
        when(jobs.due()).thenReturn(List.of("RF1"));when(jobs.claim("RF1")).thenReturn(job);
        when(channel.query("RF1")).thenThrow(new IllegalStateException("timeout"));
        new RefundWorker(jobs,channel).run();
        verify(channel,never()).refund(anyString(),anyString(),any());
        verify(jobs).finish(job,null,"timeout");
    }
    @Test void existingReceiptIsRecoveredWithoutAnotherRefundCall() {
        var receipt=new RefundChannel.Receipt(RefundChannel.State.SUCCESS,"RF1","P1",job.amount(),"provider1");
        when(jobs.due()).thenReturn(List.of("RF1"));when(jobs.claim("RF1")).thenReturn(job);when(channel.query("RF1")).thenReturn(receipt);
        new RefundWorker(jobs,channel).run();
        verify(channel,never()).refund(anyString(),anyString(),any());verify(jobs).finish(job,receipt,null);
    }
    @Test void newAttemptUsesStableLogicalRefundNumber() {
        when(jobs.due()).thenReturn(List.of("RF1"));when(jobs.claim("RF1")).thenReturn(job);
        when(channel.query("RF1")).thenReturn(new RefundChannel.Receipt(RefundChannel.State.NOT_FOUND,"RF1",null,null,null));
        var receipt=new RefundChannel.Receipt(RefundChannel.State.SUCCESS,"RF1","P1",job.amount(),"provider1");
        when(channel.refund("RF1","P1",job.amount())).thenReturn(receipt);
        new RefundWorker(jobs,channel).run();verify(jobs).finish(job,receipt,null);
    }
    @Test void staleLeaseCannotWriteMoneyInventoryOrCouponState() {
        var db=mock(JdbcTemplate.class);var refunds=mock(RefundService.class);var outbox=mock(OrderOutboxService.class);
        when(refunds.lockedRefund(null,"RF1")).thenReturn(Map.of("lease_token","new-owner","status","PROCESSING"));
        new RefundJobs(db,refunds,outbox).finish(job,new RefundChannel.Receipt(RefundChannel.State.SUCCESS,"RF1","P1",job.amount(),"provider1"),null);
        verifyNoInteractions(db,outbox);
    }
    @Test void authoritativeFailureCanBeRetriedOnlyAfterMerchantQueuesIt() {
        var retry=new RefundJobs.Job("RF1","O1","P1",job.amount(),"next",2,true);
        when(jobs.due()).thenReturn(List.of("RF1"));when(jobs.claim("RF1")).thenReturn(retry);
        when(channel.query("RF1")).thenReturn(new RefundChannel.Receipt(RefundChannel.State.FAILURE,"RF1","P1",job.amount(),null));
        new RefundWorker(jobs,channel).run();verify(channel).refund("RF1","P1",job.amount());
    }
}
