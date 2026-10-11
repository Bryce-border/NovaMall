package cn.novamall.order.service;

import java.math.BigDecimal;

/** A real adapter must authenticate receipts and return NOT_FOUND only after an authoritative query. */
public interface RefundChannel {
    enum State { NOT_FOUND, SUCCESS, FAILURE, UNKNOWN }
    record Receipt(State state, String refundNo, String paymentNo, BigDecimal amount, String channelRefundNo) { }
    Receipt query(String refundNo);
    Receipt refund(String refundNo, String paymentNo, BigDecimal amount);
}
