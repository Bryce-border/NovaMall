package cn.novamall.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayRateLimitConfig {
    @Bean
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            String address = exchange.getRequest().getRemoteAddress() == null
                    ? "unknown"
                    : String.valueOf(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
            return Mono.just("ip:" + address);
        };
    }
}
