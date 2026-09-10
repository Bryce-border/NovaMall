package cn.novamall.order.service;

import cn.novamall.order.client.UserClient;
import cn.novamall.order.mapper.CartCleanupMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class CartCleanupWorker {
    private static final Logger log = LoggerFactory.getLogger(CartCleanupWorker.class);
    private final CartCleanupMapper cleanup;
    private final UserClient users;
    private final ObjectMapper json;

    public CartCleanupWorker(CartCleanupMapper cleanup, UserClient users, ObjectMapper json) {
        this.cleanup = cleanup;
        this.users = users;
        this.json = json;
    }

    @Scheduled(fixedDelayString = "${novamall.cart.cleanup-delay-ms:5000}")
    public void processPending() {
        for (CartCleanupMapper.Task task : cleanup.pending()) {
            try {
                List<UserClient.CartCleanupItem> items = json.readValue(task.payload(), new TypeReference<>() { });
                users.cleanupCart(new UserClient.CartCleanupRequest(task.userId(), items));
                cleanup.done(task.id());
            } catch (Exception ex) {
                cleanup.retry(task.id(), Math.min(300L, 1L << Math.min(task.attempts() + 1, 8)));
                log.warn("cart cleanup failed, taskId={}", task.id(), ex);
            }
        }
    }
}
