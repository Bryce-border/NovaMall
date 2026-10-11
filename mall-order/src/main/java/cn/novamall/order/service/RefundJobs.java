package cn.novamall.order.service;

import cn.novamall.common.id.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
public class RefundJobs {
    private final JdbcTemplate db;
    private final RefundService refunds;
    private final OrderOutboxService outbox;

    public RefundJobs(JdbcTemplate db, RefundService refunds, OrderOutboxService outbox) {
        this.db = db; this.refunds = refunds; this.outbox = outbox;
    }

    public record Job(String refundNo, String orderNo, String paymentNo, BigDecimal amount, String token,
                      int attempts, boolean retryConfirmedFailure) { }

    public List<String> due() {
        return db.queryForList("""
                SELECT refund_no FROM mall_refund WHERE status IN ('QUEUED','PROCESSING','UNKNOWN')
                  AND next_attempt_at<=NOW(3) AND (lease_until IS NULL OR lease_until<NOW(3))
                ORDER BY next_attempt_at,refund_no LIMIT 20
                """, String.class);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Job claim(String no) {
        var row = refunds.lockedRefund(null, no);
        String token = UUID.randomUUID().toString();
        int changed = db.update("""
                UPDATE mall_refund SET status='PROCESSING',attempts=attempts+1,lease_token=?,
                  lease_until=DATE_ADD(NOW(3),INTERVAL 60 SECOND),updated_at=NOW(3)
                WHERE refund_no=? AND status IN ('QUEUED','PROCESSING','UNKNOWN')
                  AND next_attempt_at<=NOW(3) AND (lease_until IS NULL OR lease_until<NOW(3))
                """, token, no);
        if (changed == 0) return null;
        // An abandoned attempt is retained, with the next attempt querying the same provider reference.
        db.update("UPDATE mall_refund_attempt SET outcome='LEASE_EXPIRED',note='执行租约到期，由后续任务查单恢复',finished_at=NOW(3) WHERE refund_no=? AND outcome='STARTED'", no);
        db.update("INSERT INTO mall_refund_attempt(id,refund_no,lease_token,outcome,started_at) VALUES(?,?,?,'STARTED',NOW(3))", IdGenerator.nextId(), no, token);
        if ("QUEUED".equals(row.get("status"))) refunds.history(no, "PROCESSING", "SYSTEM", null, "开始模拟退款，先按原退款编号查询渠道结果");
        return new Job(no, row.get("order_no").toString(), row.get("payment_no").toString(),
                (BigDecimal)row.get("amount"), token, ((Number)row.get("attempts")).intValue() + 1,
                "QUEUED".equals(row.get("status")) && ((Number)row.get("attempts")).intValue() > 0);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void finish(Job job, RefundChannel.Receipt receipt, String error) {
        var row = refunds.lockedRefund(null, job.refundNo());
        if (!job.token().equals(row.get("lease_token")) || !"PROCESSING".equals(row.get("status"))) return;
        boolean matches = receipt != null && job.refundNo().equals(receipt.refundNo())
                && job.paymentNo().equals(receipt.paymentNo()) && receipt.amount() != null
                && job.amount().compareTo(receipt.amount()) == 0;
        String next = "UNKNOWN";
        String note = error == null ? "渠道结果待确认，系统稍后继续查单" : "渠道通信异常，退款结果待确认";
        if (matches && receipt.state() == RefundChannel.State.SUCCESS && receipt.channelRefundNo() != null) {
            next = "SUCCESS"; note = "模拟退款成功，未发生真实资金转移；库存按退款或验收结果异步处理";
        } else if (matches && receipt.state() == RefundChannel.State.FAILURE) {
            next = "FAILED"; note = "渠道已明确确认退款失败，可由商家重试";
        }
        long delay = Math.min(300, 1L << Math.min(job.attempts(), 8));
        db.update("""
                UPDATE mall_refund SET status=?,channel_refund_no=?,last_error=?,
                  succeeded_at=CASE WHEN ?='SUCCESS' THEN NOW(3) ELSE succeeded_at END,
                  next_attempt_at=DATE_ADD(NOW(3),INTERVAL ? SECOND),lease_token=NULL,lease_until=NULL,updated_at=NOW(3)
                WHERE refund_no=? AND lease_token=?
                """, next, matches ? receipt.channelRefundNo() : null, "SUCCESS".equals(next) ? null : note,
                next, delay, job.refundNo(), job.token());
        db.update("UPDATE mall_refund_attempt SET outcome=?,note=?,finished_at=NOW(3) WHERE lease_token=?",
                next, RefundService.truncate(error == null ? note : error), job.token());
        if (!"UNKNOWN".equals(next) || job.attempts() == 1 || job.attempts() % 10 == 0)
            refunds.history(job.refundNo(), next, "SYSTEM", null, note);
        if (!"SUCCESS".equals(next)) return;

        var lines = db.queryForList("""
                SELECT i.product_id,i.sku_id,CASE WHEN rt.refund_no IS NULL THEN ri.quantity ELSE COALESCE(rti.restock_quantity,0) END AS quantity
                FROM mall_refund_item ri JOIN mall_order_item i ON i.id=ri.order_item_id
                LEFT JOIN mall_refund_return rt ON rt.refund_no=ri.refund_no
                LEFT JOIN mall_refund_return_item rti ON rti.refund_no=ri.refund_no AND rti.order_item_id=ri.order_item_id
                WHERE ri.refund_no=? ORDER BY i.product_id,i.sku_id
                """, job.refundNo());
        var stockItems = lines.stream().filter(i -> ((Number)i.get("quantity")).intValue()>0).map(i ->
                new OrderOutboxService.RefundItemPayload(((Number)i.get("product_id")).longValue(),
                        i.get("sku_id") == null ? null : ((Number)i.get("sku_id")).longValue(),
                        ((Number)i.get("quantity")).intValue())).toList();
        if (!stockItems.isEmpty()) outbox.appendRefundEvent(job.refundNo(), job.orderNo(), stockItems);
        outbox.appendSalesSnapshot(job.orderNo());
        Integer remaining = db.queryForObject("""
                SELECT COUNT(*) FROM mall_order_item i WHERE i.order_no=? AND i.quantity > COALESCE((
                  SELECT SUM(ri.quantity) FROM mall_refund_item ri JOIN mall_refund r ON r.refund_no=ri.refund_no
                  WHERE ri.order_item_id=i.id AND r.status='SUCCESS'),0)
                """, Integer.class, job.orderNo());
        if (remaining == 0) {
            // Expired returned coupons remain expired in the wallet; their original expiry is never extended.
            db.update("UPDATE mall_user_coupon SET status='AVAILABLE',order_no=NULL WHERE order_no=? AND status='USED'", job.orderNo());
            refunds.history(job.refundNo(), "SUCCESS", "SYSTEM", null, "原订单全部商品已退；原优惠券按原有效期退回，过期券不可使用");
        }
    }
}
