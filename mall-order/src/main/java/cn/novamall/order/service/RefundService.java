package cn.novamall.order.service;

import cn.novamall.common.id.IdGenerator;
import cn.novamall.order.RefundApiModels.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RefundService {
    private final JdbcTemplate db;
    private final boolean mockEnabled;
    private static final Set<String> STATUSES = Set.of("PENDING_REVIEW", "REJECTED", "WITHDRAWN", "QUEUED",
            "PROCESSING", "UNKNOWN", "FAILED", "SUCCESS", "WAITING_RETURN", "WAITING_INSPECTION", "INSPECTION_REJECTED");

    public RefundService(JdbcTemplate db, @Value("${novamall.payment.mock-enabled:false}") boolean mockEnabled) {
        this.db = db;
        this.mockEnabled = mockEnabled;
    }

    // Always lock the original order first, also for review/withdrawal/worker completion.
    Map<String, Object> lockOrder(String no, Long user) {
        var rows = user == null
                ? db.queryForList("SELECT * FROM mall_order WHERE order_no=? FOR UPDATE", no)
                : db.queryForList("SELECT * FROM mall_order WHERE order_no=? AND user_id=? FOR UPDATE", no, user);
        if (rows.isEmpty()) throw new IllegalArgumentException("订单不存在或无权访问");
        return rows.get(0);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> eligibility(long user, String no) {
        var order = lockOrder(no, user);
        String blocked = blockedReason(order);
        var items = allocatedItems(order);
        var result = new LinkedHashMap<String, Object>();
        result.put("orderNo", no);
        result.put("mode", mode(order));
        result.put("blockedReason", blocked);
        result.put("items", clean(items));
        result.put("refundedAmount", db.queryForObject("SELECT COALESCE(SUM(amount),0) FROM mall_refund WHERE order_no=? AND status='SUCCESS'", BigDecimal.class, no).toPlainString());
        result.put("pendingAmount", db.queryForObject("SELECT COALESCE(SUM(amount),0) FROM mall_refund WHERE order_no=? AND status NOT IN ('SUCCESS','REJECTED','WITHDRAWN')", BigDecimal.class, no).toPlainString());
        result.put("hasPending", db.queryForObject("SELECT COUNT(*) FROM mall_refund WHERE order_no=? AND status NOT IN ('SUCCESS','REJECTED','WITHDRAWN')", Integer.class, no) > 0);
        return result;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> quote(long user, String no, QuoteRequest request) {
        var order = lockOrder(no, user);
        requireRefundable(order);
        var quote = calculate(order, request.items());
        return Map.of("amount", quote.amount().toPlainString(), "items", quote.lines(), "mode", mode(order));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> apply(long user, String no, ApplyRequest request) {
        var order = lockOrder(no, user);
        String hash = fingerprint(request);
        var prior = db.queryForList("SELECT * FROM mall_refund WHERE user_id=? AND order_no=? AND request_key=?", user, no, request.requestKey());
        if (!prior.isEmpty()) {
            if (!hash.equals(prior.get(0).get("request_hash"))) throw new IllegalArgumentException("同一退款请求不能修改内容，请先查看原退款单");
            return detail(user, prior.get(0).get("refund_no").toString());
        }
        requireRefundable(order);
        if (!mode(order).equals(request.expectedMode() == null ? "REFUND_ONLY" : request.expectedMode()))
            throw new IllegalArgumentException("订单发货状态已变化，请重新确认退款方式");
        var quote = calculate(order, request.items());
        if (quote.amount().compareTo(request.expectedAmount()) != 0) throw new IllegalArgumentException("可退款金额已变化，请重新计算并确认");
        var payment = payment(order);
        String refundNo = "RF" + IdGenerator.nextId();
        db.update("""
                INSERT INTO mall_refund(refund_no,order_no,user_id,user_name,payment_no,channel,request_key,request_hash,reason,amount,status,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,'PENDING_REVIEW',NOW(3),NOW(3))
                """, refundNo, no, user, order.get("user_name"), payment.get("payment_no"), payment.get("channel"),
                request.requestKey(), hash, request.reason().trim(), quote.amount());
        for (var line : quote.lines()) db.update("INSERT INTO mall_refund_item(refund_no,order_item_id,quantity,amount) VALUES(?,?,?,?)",
                refundNo, line.get("orderItemId"), line.get("quantity"), line.get("amount"));
        if ("RETURN_REFUND".equals(mode(order))) db.update("INSERT INTO mall_refund_return(refund_no,original_status,created_at,updated_at) VALUES(?,?,NOW(3),NOW(3))",
                refundNo, order.get("status"));
        history(refundNo, "PENDING_REVIEW", "USER", user, "买家申请退款：" + request.reason().trim());
        return detail(user, refundNo);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> detail(Long user, String no) {
        var rows = user == null ? db.queryForList("SELECT * FROM mall_refund WHERE refund_no=?", no)
                : db.queryForList("SELECT * FROM mall_refund WHERE refund_no=? AND user_id=?", no, user);
        if (rows.isEmpty()) throw new IllegalArgumentException("退款单不存在或无权访问");
        var refund = rows.get(0);
        refund.remove("request_hash"); refund.remove("lease_token"); refund.remove("request_key");
        var returns = db.queryForList("SELECT * FROM mall_refund_return WHERE refund_no=?", no);
        refund.put("mode", returns.isEmpty() ? "REFUND_ONLY" : "RETURN_REFUND");
        if (!returns.isEmpty()) {
            returns.get(0).remove("inspection_hash");
            refund.put("returnInfo", clean(returns).get(0));
            refund.put("restockItems", clean(db.queryForList("SELECT order_item_id,restock_quantity FROM mall_refund_return_item WHERE refund_no=?", no)));
        }
        refund.put("items", clean(db.queryForList("""
                SELECT ri.order_item_id,ri.quantity,ri.amount,i.product_id,i.sku_id,i.product_name,i.sku_name,i.cover_image_url,i.unit_price
                FROM mall_refund_item ri JOIN mall_order_item i ON i.id=ri.order_item_id WHERE ri.refund_no=? ORDER BY i.id
                """, no)));
        refund.put("history", clean(db.queryForList("SELECT status,actor_type,note,created_at FROM mall_refund_history WHERE refund_no=? ORDER BY created_at,id", no)));
        if (user == null) refund.put("execution", clean(db.queryForList("SELECT outcome,note,started_at,finished_at FROM mall_refund_attempt WHERE refund_no=? ORDER BY started_at DESC,id DESC LIMIT 50", no)));
        return clean(List.of(refund)).get(0);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> page(Long user, int page, int size, String status, String orderNo,
                                    String refundNo, String buyer, LocalDateTime from, LocalDateTime to) {
        if (page < 1 || page > 10000 || size < 1 || size > 50) throw new IllegalArgumentException("分页范围不合法");
        if (from != null && to != null && !from.isBefore(to)) throw new IllegalArgumentException("结束时间应晚于开始时间");
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (user != null) { where.append(" AND user_id=?"); args.add(user); }
        if (status != null && !status.isBlank()) {
            if (!STATUSES.contains(status)) throw new IllegalArgumentException("退款状态不合法");
            where.append(" AND status=?"); args.add(status);
        }
        filter(where, args, "order_no", orderNo, 32);
        filter(where, args, "refund_no", refundNo, 32);
        if (user == null) filter(where, args, "user_name", buyer, 32);
        if (from != null) { where.append(" AND created_at>=?"); args.add(from); }
        if (to != null) { where.append(" AND created_at<?"); args.add(to); }
        long total = db.queryForObject("SELECT COUNT(*) FROM mall_refund" + where, Long.class, args.toArray());
        args.add(size); args.add((page - 1) * size);
        var rows = db.queryForList("SELECT refund_no,order_no,user_name,amount,status,reason,created_at,updated_at FROM mall_refund" + where + " ORDER BY created_at DESC,refund_no DESC LIMIT ? OFFSET ?", args.toArray());
        return Map.of("items", clean(rows), "total", total, "page", page, "hasNext", (long) page * size < total);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> withdraw(long user, String no) {
        var refund = lockedRefund(user, no);
        if ("WITHDRAWN".equals(refund.get("status"))) return detail(user, no);
        if (!Set.of("PENDING_REVIEW", "WAITING_RETURN").contains(refund.get("status"))) throw new IllegalArgumentException("仅待审核或尚未寄回商品的申请可以撤回");
        db.update("UPDATE mall_refund SET status='WITHDRAWN',updated_at=NOW(3) WHERE refund_no=?", no);
        history(no, "WITHDRAWN", "USER", user, "买家撤回申请，释放可退数量与金额");
        return detail(user, no);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> review(long admin, String no, ReviewRequest request) {
        var refund = lockedRefund(null, no);
        String next = request.approve() ? "QUEUED" : "REJECTED";
        boolean requiresReturn = !db.queryForList("SELECT refund_no FROM mall_refund_return WHERE refund_no=?", no).isEmpty();
        if (request.approve() && requiresReturn) next = "WAITING_RETURN";
        String old = refund.get("status").toString();
        if (!"PENDING_REVIEW".equals(old)) {
            boolean approved = Set.of("QUEUED", "PROCESSING", "UNKNOWN", "FAILED", "SUCCESS", "WAITING_RETURN", "WAITING_INSPECTION", "INSPECTION_REJECTED").contains(old);
            if ((request.approve() && approved) || (!request.approve() && "REJECTED".equals(old))) return detail(null, no);
            throw new IllegalArgumentException("退款状态已变化，请刷新后操作");
        }
        if (request.approve() && !mockEnabled) throw new IllegalArgumentException("当前未启用模拟退款渠道");
        if (request.approve() && requiresReturn) {
            if (blank(request.receiverName()) || blank(request.receiverPhone()) || blank(request.returnAddress()))
                throw new IllegalArgumentException("请填写完整的退货收件人、电话和地址");
            db.update("UPDATE mall_refund_return SET receiver_name=?,receiver_phone=?,return_address=?,version=version+1,updated_at=NOW(3) WHERE refund_no=?",
                    request.receiverName().trim(), request.receiverPhone().trim(), request.returnAddress().trim(), no);
        }
        db.update("UPDATE mall_refund SET status=?,review_note=?,reviewer_id=?,reviewed_at=NOW(3),next_attempt_at=NOW(3),updated_at=NOW(3) WHERE refund_no=? AND status='PENDING_REVIEW'",
                next, request.note().trim(), admin, no);
        history(no, next, "ADMIN", admin, request.note().trim());
        return detail(null, no);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> retry(long admin, String no) {
        var refund = lockedRefund(null, no);
        if (!"FAILED".equals(refund.get("status"))) throw new IllegalArgumentException("仅渠道确认失败的退款可重试；结果待确认时系统会继续查单");
        if (!mockEnabled) throw new IllegalArgumentException("当前未启用模拟退款渠道");
        db.update("UPDATE mall_refund SET status='QUEUED',next_attempt_at=NOW(3),last_error=NULL,updated_at=NOW(3) WHERE refund_no=?", no);
        history(no, "QUEUED", "ADMIN", admin, "商家重试，继续使用原退款编号");
        return detail(null, no);
    }

    Map<String, Object> lockedRefund(Long user, String no) {
        var rows = user == null ? db.queryForList("SELECT order_no FROM mall_refund WHERE refund_no=?", no)
                : db.queryForList("SELECT order_no FROM mall_refund WHERE refund_no=? AND user_id=?", no, user);
        if (rows.isEmpty()) throw new IllegalArgumentException("退款单不存在或无权访问");
        lockOrder(rows.get(0).get("order_no").toString(), user);
        return db.queryForMap("SELECT * FROM mall_refund WHERE refund_no=? FOR UPDATE", no);
    }

    void history(String no, String status, String actor, Long user, String note) {
        db.update("INSERT INTO mall_refund_history(id,refund_no,status,actor_type,actor_id,note,created_at) VALUES(?,?,?,?,?,?,NOW(3))",
                IdGenerator.nextId(), no, status, actor, user, truncate(note));
    }

    private String blockedReason(Map<String, Object> order) {
        if (!Set.of("PAID", "SHIPPED", "COMPLETED").contains(order.get("status"))) return "仅已付款、已发货或已完成订单可以申请退款";
        if ("COMPLETED".equals(order.get("status"))) {
            Object completed = order.get("completed_at");
            LocalDateTime time = completed instanceof java.sql.Timestamp t ? t.toLocalDateTime() : (LocalDateTime) completed;
            if (time == null || !time.plusDays(7).isAfter(LocalDateTime.now())) return "订单已超过确认收货后 7 天的退货申请期";
        }
        if (!mockEnabled) return "当前未启用模拟退款渠道";
        if (amount(order, "freight_amount").signum() != 0) return "含运费订单需单独核算，暂不支持自动退款";
        var payment = payment(order);
        if (payment == null) return "原订单缺少可核对的成功支付记录，请联系商家核实";
        return "";
    }

    private void requireRefundable(Map<String, Object> order) {
        String reason = blockedReason(order);
        if (!reason.isEmpty()) throw new IllegalArgumentException(reason);
    }

    private Map<String, Object> payment(Map<String, Object> order) {
        var rows = db.queryForList("SELECT payment_no,channel,amount FROM mall_payment WHERE order_id=? AND status='SUCCESS' ORDER BY id", order.get("id"));
        if (rows.size() != 1) return null;
        var payment = rows.get(0);
        return "MOCK".equals(payment.get("channel")) && payment.get("payment_no") != null
                && amount(payment, "amount").compareTo(amount(order, "total_amount")) == 0 ? payment : null;
    }

    private List<Map<String, Object>> allocatedItems(Map<String, Object> order) {
        String no = order.get("order_no").toString();
        var items = db.queryForList("SELECT * FROM mall_order_item WHERE order_no=? ORDER BY id", no);
        BigDecimal gross = items.stream().map(i -> amount(i, "amount")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = amount(order, "total_amount").subtract(amount(order, "freight_amount"));
        if (net.signum() < 0 || net.compareTo(gross) > 0 || gross.compareTo(amount(order, "product_amount")) != 0
                || gross.subtract(amount(order, "discount_amount")).compareTo(net) != 0)
            throw new IllegalArgumentException("原订单金额快照不一致，需要人工核实");
        BigDecimal cumulative = BigDecimal.ZERO, previous = BigDecimal.ZERO;
        for (var item : items) {
            cumulative = cumulative.add(amount(item, "amount"));
            BigDecimal allocated = gross.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                    : net.multiply(cumulative).divide(gross, 2, RoundingMode.DOWN);
            BigDecimal itemNet = allocated.subtract(previous);
            previous = allocated;
            db.update("INSERT INTO mall_refund_allocation(order_item_id,order_no,net_amount,created_at) VALUES(?,?,?,NOW(3)) ON DUPLICATE KEY UPDATE order_item_id=order_item_id",
                    item.get("id"), no, itemNet);
        }
        return db.queryForList("""
                SELECT i.id AS order_item_id,i.product_name,i.sku_name,i.cover_image_url,i.quantity,i.unit_price,a.net_amount,
                  COALESCE(u.used_quantity,0) AS reserved_quantity,COALESCE(u.used_amount,0) AS reserved_amount,
                  COALESCE(u.refunded_quantity,0) AS refunded_quantity,
                  i.quantity-COALESCE(u.used_quantity,0) AS remaining_quantity,
                  a.net_amount-COALESCE(u.used_amount,0) AS remaining_amount
                FROM mall_order_item i JOIN mall_refund_allocation a ON a.order_item_id=i.id
                LEFT JOIN (
                  SELECT ri.order_item_id,SUM(ri.quantity) AS used_quantity,SUM(ri.amount) AS used_amount,
                    SUM(CASE WHEN r.status='SUCCESS' THEN ri.quantity ELSE 0 END) AS refunded_quantity
                  FROM mall_refund_item ri JOIN mall_refund r ON r.refund_no=ri.refund_no
                  WHERE r.order_no=? AND r.status NOT IN ('REJECTED','WITHDRAWN') GROUP BY ri.order_item_id
                ) u ON u.order_item_id=i.id WHERE i.order_no=? ORDER BY i.id
                """, no, no);
    }

    private Quote calculate(Map<String, Object> order, List<Line> requested) {
        Map<Long, Map<String, Object>> eligible = new HashMap<>();
        allocatedItems(order).forEach(i -> eligible.put(((Number)i.get("order_item_id")).longValue(), i));
        Set<Long> seen = new HashSet<>();
        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (var line : requested) {
            var item = eligible.get(line.orderItemId());
            if (item == null || !seen.add(line.orderItemId()) || line.quantity() < 1
                    || line.quantity() > ((Number)item.get("remaining_quantity")).intValue())
                throw new IllegalArgumentException("选择的商品或数量不可退，可能已有退款申请，请刷新订单");
            int remaining = ((Number)item.get("remaining_quantity")).intValue();
            // Each non-final quantity uses whole cents; the last remaining units absorb the remainder.
            BigDecimal value = line.quantity() == remaining ? amount(item, "remaining_amount")
                    : amount(item, "net_amount").divide(BigDecimal.valueOf(((Number)item.get("quantity")).intValue()), 2, RoundingMode.DOWN)
                        .multiply(BigDecimal.valueOf(line.quantity())).min(amount(item, "remaining_amount"));
            lines.add(Map.of("orderItemId", line.orderItemId().toString(), "quantity", line.quantity(), "amount", value.toPlainString(), "productName", item.get("product_name")));
            total = total.add(value);
        }
        return new Quote(total, lines);
    }

    private record Quote(BigDecimal amount, List<Map<String, Object>> lines) { }

    private String fingerprint(ApplyRequest r) {
        StringBuilder text = new StringBuilder(r.reason().trim()).append('|').append(r.expectedAmount().setScale(2));
        r.items().stream().sorted(Comparator.comparing(Line::orderItemId)).forEach(i -> text.append('|').append(i.orderItemId()).append(':').append(i.quantity()));
        if ("RETURN_REFUND".equals(r.expectedMode())) text.append("|RETURN_REFUND");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private void filter(StringBuilder where, List<Object> args, String column, String value, int max) {
        if (value == null || value.isBlank()) return;
        if (value.length() > max) throw new IllegalArgumentException("查询条件过长");
        where.append(" AND ").append(column).append("=?"); args.add(value.trim());
    }

    private String mode(Map<String,Object> order) { return "PAID".equals(order.get("status")) ? "REFUND_ONLY" : "RETURN_REFUND"; }
    private boolean blank(String text) { return text == null || text.isBlank(); }

    static BigDecimal amount(Map<String, Object> row, String key) { return (BigDecimal) row.get(key); }
    static String truncate(String text) { return text == null ? "" : text.substring(0, Math.min(text.length(), 500)); }
    static List<Map<String, Object>> clean(List<Map<String, Object>> rows) {
        CouponService.clean(rows);
        rows.forEach(r -> r.replaceAll((k, v) -> v instanceof BigDecimal n ? n.toPlainString() : v));
        return rows;
    }
}
