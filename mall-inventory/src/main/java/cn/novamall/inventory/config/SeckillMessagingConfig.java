package cn.novamall.inventory.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SeckillMessagingConfig {
    public static final String EVENTS_EXCHANGE = "mall.events";
    public static final String SECKILL_QUEUE = "mall.inventory.seckill-order";

    @Bean
    public TopicExchange seckillEventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue seckillOrderQueue() {
        return new Queue(SECKILL_QUEUE, true);
    }

    @Bean
    public Binding seckillOrderBinding(Queue seckillOrderQueue,
                                       TopicExchange seckillEventsExchange) {
        return BindingBuilder.bind(seckillOrderQueue).to(seckillEventsExchange).with("seckill.order");
    }
}
