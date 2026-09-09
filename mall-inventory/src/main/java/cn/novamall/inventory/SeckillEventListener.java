package cn.novamall.inventory;

import cn.novamall.inventory.service.SeckillApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SeckillEventListener {
    private final ObjectMapper objectMapper;
    private final SeckillApplicationService seckillService;

    public SeckillEventListener(ObjectMapper objectMapper, SeckillApplicationService seckillService) {
        this.objectMapper = objectMapper;
        this.seckillService = seckillService;
    }

    @RabbitListener(queues = "mall.inventory.seckill-order")
    public void consume(String payload) throws Exception {
        seckillService.consume(objectMapper.readValue(payload, SeckillApplicationService.SeckillEvent.class));
    }
}
