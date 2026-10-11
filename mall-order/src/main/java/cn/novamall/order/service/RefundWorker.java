package cn.novamall.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "novamall.payment.mock-enabled", havingValue = "true")
public class RefundWorker {
    private static final Logger log = LoggerFactory.getLogger(RefundWorker.class);
    private final RefundJobs jobs;
    private final RefundChannel channel;
    public RefundWorker(RefundJobs jobs, RefundChannel channel) { this.jobs = jobs; this.channel = channel; }

    @Scheduled(fixedDelayString = "${novamall.refund.worker-delay-ms:3000}")
    public void run() {
        for (String no : jobs.due()) {
            try {
                var job = jobs.claim(no);
                if (job == null) continue;
                RefundChannel.Receipt receipt = null;
                String error = null;
                try {
                    // No local transaction or order lock is held during channel calls.
                    receipt = channel.query(no);
                    boolean confirmedRetry = job.retryConfirmedFailure() && receipt.state() == RefundChannel.State.FAILURE
                            && no.equals(receipt.refundNo()) && job.paymentNo().equals(receipt.paymentNo())
                            && receipt.amount() != null && job.amount().compareTo(receipt.amount()) == 0;
                    if (receipt.state() == RefundChannel.State.NOT_FOUND || confirmedRetry)
                        receipt = channel.refund(no, job.paymentNo(), job.amount());
                } catch (RuntimeException ex) {
                    error = ex.getMessage();
                    log.warn("refund channel result uncertain, refundNo={}", no, ex);
                }
                jobs.finish(job, receipt, error);
            } catch (RuntimeException ex) {
                log.error("refund job deferred; durable lease allows recovery, refundNo={}", no, ex);
            }
        }
    }
}
