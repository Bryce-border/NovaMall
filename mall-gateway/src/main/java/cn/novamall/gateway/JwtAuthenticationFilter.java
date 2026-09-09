package cn.novamall.gateway;

import cn.novamall.common.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(@Value("${security.jwt.secret}") String secret) {
        this.jwtTokenService = new JwtTokenService(secret);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/api/auth/") || path.equals("/api/users/register")
                || path.startsWith("/actuator/") || path.equals("/swagger-ui.html")
                || path.startsWith("/swagger-ui/") || path.startsWith("/v3/api-docs")) {
            return chain.filter(exchange);
        }
        String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (!jwtTokenService.isValid(token)) {
            return unauthorized(exchange);
        }
        Long userId = jwtTokenService.userId(token);
        String username = jwtTokenService.subject(token);
        ServerWebExchange authenticatedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Name");
                    if (userId != null) {
                        headers.add("X-User-Id", String.valueOf(userId));
                    }
                    headers.add("X-User-Name", username);
                }))
                .build();
        return chain.filter(authenticatedExchange);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"success\":false,\"message\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
