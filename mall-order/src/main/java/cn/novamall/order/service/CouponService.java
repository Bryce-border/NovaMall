package cn.novamall.order.service;

import cn.novamall.common.id.IdGenerator;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class CouponService {
    private final JdbcTemplate db;
    private final boolean mockEnabled;
    public CouponService(JdbcTemplate db, @Value("${novamall.payment.mock-enabled:false}") boolean mockEnabled) {
        this.db = db; this.mockEnabled = mockEnabled;
    }
    public record CampaignRequest(@NotBlank @Size(max=100) String name,
        @NotNull @Pattern(regexp="FREE|PACK") String kind,
        @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal thresholdAmount,
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal discountAmount,
        @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal packPrice,
        @Min(1) @Max(20) int packSize, @Min(1) @Max(100000) int totalQuantity,
        @NotNull LocalDateTime opensAt, @NotNull LocalDateTime closesAt, @NotNull LocalDateTime expiresAt) {}
    public record ClaimRequest(boolean confirmMockPayment) {}
    public List<Map<String,Object>> campaigns(boolean admin) {
        return clean(db.queryForList("SELECT *, NOW() AS server_time FROM mall_coupon_campaign "
            + (admin ? "" : "WHERE status=1 ") + "ORDER BY opens_at DESC,id DESC LIMIT 200"));
    }
    @Transactional
    public String create(CampaignRequest r) {
        if (!r.opensAt().isBefore(r.closesAt()) || r.expiresAt().isBefore(r.closesAt())
                || !r.closesAt().isAfter(LocalDateTime.now()) || r.discountAmount().compareTo(r.thresholdAmount()) > 0) {
            throw new IllegalArgumentException("请检查开放时间、截止时间、有效期及满减金额");
        }
        if (r.kind().equals("FREE") && (r.packSize()!=1 || r.packPrice().signum()!=0)
            || r.kind().equals("PACK") && r.packPrice().signum()<=0) {
            throw new IllegalArgumentException("免费券每份 1 张且价格为 0；券包价格必须大于 0");
        }
        long id=IdGenerator.nextId();
        db.update("INSERT INTO mall_coupon_campaign(id,name,kind,threshold_amount,discount_amount,pack_price,pack_size,total_quantity,opens_at,closes_at,expires_at,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,NOW())",
            id,r.name().trim(),r.kind(),r.thresholdAmount(),r.discountAmount(),r.packPrice(),r.packSize(),r.totalQuantity(),r.opensAt(),r.closesAt(),r.expiresAt());
        return String.valueOf(id);
    }
    @Transactional
    public void status(long id, int status) {
        if (status!=0 && status!=1) throw new IllegalArgumentException("活动状态不合法");
        if(db.update("UPDATE mall_coupon_campaign SET status=? WHERE id=?",status,id)!=1)
            throw new IllegalArgumentException("活动不存在");
    }
    @Transactional
    public List<Map<String,Object>> claim(long userId, long id, boolean confirm) {
        var rows=db.queryForList("SELECT * FROM mall_coupon_campaign WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new IllegalArgumentException("活动不存在");
        var c=rows.get(0);
        // The locked campaign row and unique buyer key serialize retries and last-unit claims.
        var prior=db.queryForList("SELECT id FROM mall_coupon_claim WHERE campaign_id=? AND user_id=?",id,userId);
        if(!prior.isEmpty()) return claimCoupons(prior.get(0).get("id"));
        LocalDateTime now=LocalDateTime.now();
        if(integer(c,"status")!=1 || now.isBefore(time(c,"opens_at")) || !now.isBefore(time(c,"closes_at")))
            throw new IllegalArgumentException("活动尚未开放或已结束");
        if(integer(c,"issued_quantity")>=integer(c,"total_quantity")) throw new IllegalArgumentException("本场优惠券已抢完");
        if("PACK".equals(c.get("kind")) && (!mockEnabled || !confirm))
            throw new IllegalArgumentException("请确认模拟支付后购买券包");
        long claimId=IdGenerator.nextId();
        db.update("INSERT INTO mall_coupon_claim(id,campaign_id,user_id,paid_amount,created_at) VALUES(?,?,?,?,NOW())",claimId,id,userId,c.get("pack_price"));
        db.update("UPDATE mall_coupon_campaign SET issued_quantity=issued_quantity+1 WHERE id=?",id);
        for(int i=0;i<integer(c,"pack_size");i++) {
            db.update("INSERT INTO mall_user_coupon(id,claim_id,campaign_id,user_id,name,threshold_amount,discount_amount,expires_at,created_at) VALUES(?,?,?,?,?,?,?,?,NOW())",
                IdGenerator.nextId(),claimId,id,userId,c.get("name"),c.get("threshold_amount"),c.get("discount_amount"),c.get("expires_at"));
        }
        return claimCoupons(claimId);
    }
    private List<Map<String,Object>> claimCoupons(Object id) {
        return clean(db.queryForList("SELECT * FROM mall_user_coupon WHERE claim_id=? ORDER BY id",id));
    }
    public List<Map<String,Object>> wallet(long userId) {
        return clean(db.queryForList("SELECT *, CASE WHEN status='AVAILABLE' AND expires_at<=NOW() THEN 'EXPIRED' ELSE status END AS display_status FROM mall_user_coupon WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 1000",userId));
    }
    // Called inside the existing cart checkout transaction, after its idempotency row lock.
    public void bindCheckout(long userId,String requestKey,Long couponId) {
        db.update("INSERT INTO mall_checkout_coupon(user_id,request_key,coupon_id) VALUES(?,?,?) ON DUPLICATE KEY UPDATE request_key=request_key",userId,requestKey,couponId);
        var row=db.queryForMap("SELECT coupon_id FROM mall_checkout_coupon WHERE user_id=? AND request_key=?",userId,requestKey);
        Object stored=row.get("coupon_id");
        if(!Objects.equals(stored==null?null:((Number)stored).longValue(),couponId))
            throw new IllegalArgumentException("同一结算请求不能更换优惠券，请使用原请求重试");
    }
    @Transactional
    public BigDecimal reserve(long userId, Long couponId, String orderNo, BigDecimal amount) {
        if(couponId==null) return BigDecimal.ZERO;
        var rows=db.queryForList("SELECT * FROM mall_user_coupon WHERE id=? AND user_id=? FOR UPDATE",couponId,userId);
        if(rows.isEmpty()) throw new IllegalArgumentException("优惠券不存在或不属于当前账号");
        var c=rows.get(0);
        if(!"AVAILABLE".equals(c.get("status")) || !time(c,"expires_at").isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("优惠券已使用、已锁定或已过期");
        if(amount.compareTo((BigDecimal)c.get("threshold_amount"))<0) throw new IllegalArgumentException("订单金额未达到优惠券使用门槛");
        db.update("UPDATE mall_user_coupon SET status='LOCKED',order_no=? WHERE id=?",orderNo,couponId);
        return ((BigDecimal)c.get("discount_amount")).min(amount);
    }
    public void consume(String orderNo) { db.update("UPDATE mall_user_coupon SET status='USED' WHERE order_no=? AND status='LOCKED'",orderNo); }
    public void release(String orderNo) { db.update("UPDATE mall_user_coupon SET status='AVAILABLE',order_no=NULL WHERE order_no=? AND status='LOCKED'",orderNo); }
    private static int integer(Map<String,Object> m,String key){return ((Number)m.get(key)).intValue();}
    private static LocalDateTime time(Map<String,Object> m,String key){Object v=m.get(key);return v instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)v;}
    public static List<Map<String,Object>> clean(List<Map<String,Object>> rows){
        rows.forEach(row->row.replaceAll((k,v)->v instanceof Long? v.toString():v instanceof Timestamp t?t.toLocalDateTime().toString():v));
        return rows;
    }
}
