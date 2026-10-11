package cn.novamall.order.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
@ConditionalOnProperty(name = "novamall.payment.mock-enabled", havingValue = "true")
public class MockRefundChannel implements RefundChannel {
    private final JdbcTemplate db;
    public MockRefundChannel(JdbcTemplate db) { this.db = db; }

    @Override
    public Receipt query(String no) {
        var rows = db.queryForList("SELECT * FROM mall_mock_refund_receipt WHERE refund_no=?", no);
        if (rows.isEmpty()) return new Receipt(State.NOT_FOUND, no, null, null, null);
        var r = rows.get(0);
        return new Receipt(State.SUCCESS, no, r.get("payment_no").toString(), (BigDecimal)r.get("amount"), r.get("channel_refund_no").toString());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Receipt refund(String no, String paymentNo, BigDecimal amount) {
        db.update("INSERT INTO mall_mock_refund_receipt(refund_no,payment_no,amount,channel_refund_no,created_at) VALUES(?,?,?,?,NOW(3)) ON DUPLICATE KEY UPDATE refund_no=refund_no",
                no, paymentNo, amount, "MOCK-REFUND-" + no);
        Receipt receipt = query(no);
        if (!paymentNo.equals(receipt.paymentNo()) || amount.compareTo(receipt.amount()) != 0)
            throw new IllegalStateException("模拟渠道退款编号对应的支付单或金额不一致");
        return receipt;
    }
}
