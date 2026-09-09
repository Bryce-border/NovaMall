package cn.novamall.order.service;

import cn.novamall.order.entity.OrderEntity;
import cn.novamall.order.mapper.OrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OrderTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScheduler.class);

    private final OrderMapper orderMapper;
    private final OrderApplicationService orderService;

    public OrderTimeoutScheduler(OrderMapper orderMapper, OrderApplicationService orderService) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
    }

    @Scheduled(fixedDelayString = "${novamall.order.timeout-scan-delay-ms:60000}", initialDelayString = "${novamall.order.timeout-initial-delay-ms:60000}")
    public void cancelExpiredOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        List<OrderEntity> expired = orderMapper.findPendingBefore(cutoff, 100);
        for (OrderEntity order : expired) {
            try {
                orderService.cancelExpired(order.getOrderNo());
            } catch (RuntimeException ex) {
                log.error("failed to cancel expired order, orderNo={}", order.getOrderNo(), ex);
            }
        }
    }
}
