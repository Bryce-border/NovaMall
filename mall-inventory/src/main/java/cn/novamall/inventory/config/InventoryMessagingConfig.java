package cn.novamall.inventory.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class InventoryMessagingConfig {

    public static final String EVENTS_EXCHANGE = "mall.events";
    public static final String DEAD_LETTER_EXCHANGE = "mall.events.dlx";
    public static final String CANCEL_QUEUE = "mall.inventory.order-cancelled";
    public static final String CANCEL_DLQ = "mall.inventory.order-cancelled.dlq";
    public static final String PAID_QUEUE = "mall.inventory.order-paid";
    public static final String PAID_DLQ = "mall.inventory.order-paid.dlq";

    @Bean
    public Queue refundSucceededQueue() {
        return new Queue("mall.inventory.refund-succeeded", true, false, false, Map.of(
                "x-dead-letter-exchange", DEAD_LETTER_EXCHANGE,
                "x-dead-letter-routing-key", "inventory.refund-succeeded.dlq"));
    }

    @Bean
    public Queue refundSucceededDeadLetterQueue() { return new Queue("mall.inventory.refund-succeeded.dlq", true); }

    @Bean
    public Binding refundSucceededBinding(@Qualifier("refundSucceededQueue") Queue queue,
                                          @Qualifier("mallEventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("refund.succeeded");
    }

    @Bean
    public Binding refundSucceededDeadLetterBinding(@Qualifier("refundSucceededDeadLetterQueue") Queue queue,
                                                    @Qualifier("mallEventsDeadLetterExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("inventory.refund-succeeded.dlq");
    }

    @Bean
    public Queue orderPaidQueue() {
        return new Queue(PAID_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", DEAD_LETTER_EXCHANGE,
                "x-dead-letter-routing-key", "inventory.order-paid.dlq"));
    }

    @Bean
    public Queue orderPaidDeadLetterQueue() { return new Queue(PAID_DLQ, true); }

    @Bean
    public Binding orderPaidBinding(@Qualifier("orderPaidQueue") Queue queue,
                                    @Qualifier("mallEventsExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("order.paid");
    }

    @Bean
    public Binding orderPaidDeadLetterBinding(@Qualifier("orderPaidDeadLetterQueue") Queue queue,
                                              @Qualifier("mallEventsDeadLetterExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange).with("inventory.order-paid.dlq");
    }

    @Bean
    public TopicExchange mallEventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange mallEventsDeadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCancelledQueue() {
        return new Queue(CANCEL_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", DEAD_LETTER_EXCHANGE,
                "x-dead-letter-routing-key", "inventory.order-cancelled.dlq"));
    }

    @Bean
    public Queue orderCancelledDeadLetterQueue() {
        return new Queue(CANCEL_DLQ, true);
    }

    @Bean
    public Binding orderCancelledBinding(@Qualifier("orderCancelledQueue") Queue orderCancelledQueue,
                                         @Qualifier("mallEventsExchange") TopicExchange mallEventsExchange) {
        return BindingBuilder.bind(orderCancelledQueue).to(mallEventsExchange).with("order.cancelled");
    }

    @Bean
    public Binding orderCancelledDeadLetterBinding(
            @Qualifier("orderCancelledDeadLetterQueue") Queue orderCancelledDeadLetterQueue,
            @Qualifier("mallEventsDeadLetterExchange") TopicExchange mallEventsDeadLetterExchange) {
        return BindingBuilder.bind(orderCancelledDeadLetterQueue)
                .to(mallEventsDeadLetterExchange).with("inventory.order-cancelled.dlq");
    }
}
