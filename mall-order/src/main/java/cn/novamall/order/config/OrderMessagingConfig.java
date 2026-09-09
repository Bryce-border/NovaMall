package cn.novamall.order.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderMessagingConfig {

    public static final String EVENTS_EXCHANGE = "mall.events";
    public static final String ORDER_CREATED_QUEUE = "mall.order.created";

    @Bean
    public TopicExchange mallEventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCreatedQueue() {
        return new Queue(ORDER_CREATED_QUEUE, true);
    }

    @Bean
    public Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange mallEventsExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(mallEventsExchange).with("order.created");
    }
}
