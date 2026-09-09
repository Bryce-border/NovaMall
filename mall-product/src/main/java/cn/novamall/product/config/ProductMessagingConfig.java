package cn.novamall.product.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

@Configuration
public class ProductMessagingConfig {
    public static final String EVENTS_EXCHANGE = "mall.events";
    public static final String DEAD_LETTER_EXCHANGE = "mall.events.dlx";
    public static final String SALES_QUEUE = "mall.product.order-completed";
    public static final String SALES_DLQ = "mall.product.order-completed.dlq";

    @Bean
    public TopicExchange productEventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange productDeadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue salesQueue() {
        return new Queue(SALES_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", DEAD_LETTER_EXCHANGE,
                "x-dead-letter-routing-key", "product.order-completed.dlq"));
    }

    @Bean
    public Queue salesDeadLetterQueue() { return new Queue(SALES_DLQ, true); }

    @Bean
    public Binding salesBinding(@Qualifier("salesQueue") Queue queue,
                                @Qualifier("productEventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("order.completed");
    }

    @Bean
    public Binding salesDeadLetterBinding(@Qualifier("salesDeadLetterQueue") Queue queue,
                                          @Qualifier("productDeadLetterExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("product.order-completed.dlq");
    }
}
