package cn.novamall.product.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ProductSalesService {
    private final JdbcTemplate db;
    public ProductSalesService(JdbcTemplate db){this.db=db;}
    public record Line(Long productId,Long quantity) { }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void apply(String orderNo,long version,List<Line> items) {
        if(orderNo==null||orderNo.isBlank()||orderNo.length()>32||version<1||items==null||items.size()>100)
            throw new IllegalArgumentException("invalid sales snapshot");
        Map<Long,Long> next=new TreeMap<>();
        for(var item:items) {
            if(item==null||item.productId()==null||item.productId()<=0||item.quantity()==null||item.quantity()<0
                    ||next.putIfAbsent(item.productId(),item.quantity())!=null)throw new IllegalArgumentException("invalid sales item");
        }
        db.update("INSERT INTO mall_product_sales_order(order_no,version) VALUES(?,-1) ON DUPLICATE KEY UPDATE order_no=order_no",orderNo);
        long current=db.queryForObject("SELECT version FROM mall_product_sales_order WHERE order_no=? FOR UPDATE",Long.class,orderNo);
        if(current>=version)return;
        Map<Long,Long> previous=new TreeMap<>();
        db.queryForList("SELECT product_id,quantity FROM mall_product_sales_line WHERE order_no=?",orderNo)
                .forEach(i->previous.put(((Number)i.get("product_id")).longValue(),((Number)i.get("quantity")).longValue()));
        Set<Long> ids=new TreeSet<>(previous.keySet());ids.addAll(next.keySet());
        for(long id:ids) {
            long delta=Math.subtractExact(next.getOrDefault(id,0L),previous.getOrDefault(id,0L));
            db.update("INSERT INTO mall_product_sales_total(product_id,quantity) VALUES(?,0) ON DUPLICATE KEY UPDATE product_id=product_id",id);
            if(db.update("UPDATE mall_product_sales_total SET quantity=quantity+? WHERE product_id=? AND quantity+?>=0",delta,id,delta)!=1)
                throw new IllegalStateException("sales projection would become negative");
            db.update("INSERT INTO mall_product_sales_line(order_no,product_id,quantity) VALUES(?,?,?) ON DUPLICATE KEY UPDATE quantity=VALUES(quantity)",orderNo,id,next.getOrDefault(id,0L));
        }
        db.update("UPDATE mall_product_sales_order SET version=? WHERE order_no=?",version,orderNo);
    }
}
