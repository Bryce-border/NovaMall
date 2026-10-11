package cn.novamall.product;

import cn.novamall.product.service.ProductSalesService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class ProductSalesListener {
    private final ObjectMapper json;private final ProductSalesService sales;
    public ProductSalesListener(ObjectMapper json,ProductSalesService sales){this.json=json;this.sales=sales;}
    @RabbitListener(queues="mall.product.sales-updated")
    public void onSales(String payload)throws Exception {
        var event=json.readValue(payload,Event.class);
        if(event==null||!"ORDER_SALES_UPDATED".equals(event.eventType()))throw new IllegalArgumentException("invalid sales event");
        sales.apply(event.orderNo(),event.version(),event.items());
    }
    public record Event(String eventId,String eventType,String orderNo,long version,List<ProductSalesService.Line> items) { }
}
