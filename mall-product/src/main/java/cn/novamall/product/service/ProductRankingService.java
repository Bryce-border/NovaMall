package cn.novamall.product.service;

import cn.novamall.product.ProductApiModels;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ProductRankingService {
    private final JdbcTemplate db;
    public ProductRankingService(JdbcTemplate db){this.db=db;}

    public List<ProductApiModels.SalesProductResponse> topSales(long limit) {
        return db.query("""
                SELECT p.id,p.name,p.price,s.quantity FROM mall_product_sales_total s JOIN mall_product p ON p.id=s.product_id
                WHERE p.deleted=0 AND p.status=1 AND s.quantity>0 ORDER BY s.quantity DESC,p.id LIMIT ?
                """,(rs,n)->new ProductApiModels.SalesProductResponse(rs.getLong("id"),rs.getString("name"),rs.getBigDecimal("price"),rs.getDouble("quantity")),Math.max(1,Math.min(limit,100)));
    }

    public List<ProductApiModels.SalesCountResponse> salesCounts(List<Long> productIds) {
        if(productIds.isEmpty())return List.of();
        var ids=productIds.stream().distinct().toList();
        var placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
        Map<Long,Long> counts=new HashMap<>();
        db.queryForList("SELECT product_id,quantity FROM mall_product_sales_total WHERE product_id IN ("+placeholders+")",ids.toArray())
                .forEach(r->counts.put(((Number)r.get("product_id")).longValue(),((Number)r.get("quantity")).longValue()));
        return ids.stream().map(id->new ProductApiModels.SalesCountResponse(id.toString(),counts.getOrDefault(id,0L))).toList();
    }
}
