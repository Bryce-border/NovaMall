package cn.novamall.order.service;

import cn.novamall.order.RefundApiModels.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class RefundReturnService {
    private final JdbcTemplate db;
    private final RefundService refunds;
    public RefundReturnService(JdbcTemplate db, RefundService refunds) { this.db=db; this.refunds=refunds; }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String,Object> logistics(long user, String no, LogisticsRequest request) {
        var refund = refunds.lockedRefund(user, no);
        var returned = requireReturn(no);
        if (request.carrier().trim().equals(returned.get("carrier")) && request.trackingNo().equals(returned.get("tracking_no")))
            return refunds.detail(user, no);
        if (!Set.of("WAITING_RETURN","WAITING_INSPECTION").contains(refund.get("status")))
            throw new IllegalArgumentException("当前状态不能填写或修改退货物流");
        checkVersion(returned, request.version());
        db.update("UPDATE mall_refund_return SET carrier=?,tracking_no=?,shipped_at=NOW(3),version=version+1,updated_at=NOW(3) WHERE refund_no=?",
                request.carrier().trim(), request.trackingNo(), no);
        db.update("UPDATE mall_refund SET status='WAITING_INSPECTION',updated_at=NOW(3) WHERE refund_no=?", no);
        refunds.history(no,"WAITING_INSPECTION","USER",user,"买家提交退货物流："+request.carrier().trim()+" / "+request.trackingNo());
        return refunds.detail(user,no);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String,Object> inspect(long admin, String no, InspectRequest request) {
        var refund = refunds.lockedRefund(null, no);
        var returned = requireReturn(no);
        String hash = fingerprint(request);
        if (hash.equals(returned.get("inspection_hash"))) return refunds.detail(null,no);
        if (!"WAITING_INSPECTION".equals(refund.get("status"))) throw new IllegalArgumentException("退货当前不在待验收状态，请刷新");
        checkVersion(returned, request.version());
        var lines = db.queryForList("SELECT order_item_id,quantity FROM mall_refund_item WHERE refund_no=?", no);
        Map<Long,Integer> quantities = new HashMap<>();
        lines.forEach(i->quantities.put(((Number)i.get("order_item_id")).longValue(),((Number)i.get("quantity")).intValue()));
        if (!request.accepted() && !request.restockItems().isEmpty()) throw new IllegalArgumentException("验收不通过时不能补库存");
        if (request.accepted()) {
            Set<Long> seen = new HashSet<>();
            for (var line : request.restockItems()) {
                if (!seen.add(line.orderItemId()) || !quantities.containsKey(line.orderItemId())
                        || line.quantity()<0 || line.quantity()>quantities.get(line.orderItemId()))
                    throw new IllegalArgumentException("补库数量不能超过该商品的退货数量，且商品不能重复");
            }
            if (seen.size()!=quantities.size()) throw new IllegalArgumentException("请逐项确认可补库数量，损坏或不可再售商品填写 0");
            for(var line:request.restockItems()) db.update("INSERT INTO mall_refund_return_item(refund_no,order_item_id,restock_quantity) VALUES(?,?,?)",
                    no,line.orderItemId(),line.quantity());
        }
        String next=request.accepted()?"QUEUED":"INSPECTION_REJECTED";
        db.update("UPDATE mall_refund_return SET inspection_result=?,inspection_note=?,inspector_id=?,inspected_at=NOW(3),inspection_hash=?,version=version+1,updated_at=NOW(3) WHERE refund_no=?",
                request.accepted()?"ACCEPTED":"REJECTED",request.note().trim(),admin,hash,no);
        db.update("UPDATE mall_refund SET status=?,next_attempt_at=CASE WHEN ?='QUEUED' THEN NOW(3) ELSE NULL END,updated_at=NOW(3) WHERE refund_no=?",next,next,no);
        refunds.history(no,next,"ADMIN",admin,(request.accepted()?"验收通过，已确认收到申请中的全部商品；":"验收不通过，等待买家复核申请；")+request.note().trim());
        return refunds.detail(null,no);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String,Object> recheck(long user,String no,RecheckRequest request) {
        var refund=refunds.lockedRefund(user,no);
        var returned=requireReturn(no);
        // A retry after successfully reopening cannot create another history transition.
        if ("WAITING_INSPECTION".equals(refund.get("status")) && ((Number)returned.get("version")).intValue()==request.version()+1
                && "REJECTED".equals(returned.get("inspection_result"))) return refunds.detail(user,no);
        if (!"INSPECTION_REJECTED".equals(refund.get("status"))) throw new IllegalArgumentException("仅验收未通过的退货可以申请复核");
        checkVersion(returned,request.version());
        db.update("UPDATE mall_refund_return SET version=version+1,updated_at=NOW(3) WHERE refund_no=?",no);
        db.update("UPDATE mall_refund SET status='WAITING_INSPECTION',updated_at=NOW(3) WHERE refund_no=?",no);
        refunds.history(no,"WAITING_INSPECTION","USER",user,"买家申请复核："+request.note().trim());
        return refunds.detail(user,no);
    }

    private Map<String,Object> requireReturn(String no) {
        var rows=db.queryForList("SELECT * FROM mall_refund_return WHERE refund_no=?",no);
        if(rows.isEmpty())throw new IllegalArgumentException("该申请不需要寄回商品");
        return rows.get(0);
    }
    private void checkVersion(Map<String,Object> row,int expected) {
        if(((Number)row.get("version")).intValue()!=expected)throw new IllegalArgumentException("退货信息已变化，请刷新后重新核对");
    }
    private String fingerprint(InspectRequest request) {
        StringBuilder value=new StringBuilder().append(request.version()).append('|').append(request.accepted()).append('|').append(request.note().trim());
        request.restockItems().stream().sorted(Comparator.comparing(RestockLine::orderItemId)).forEach(i->value.append('|').append(i.orderItemId()).append(':').append(i.quantity()));
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
}
