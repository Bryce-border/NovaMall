package cn.novamall.gateway;

import cn.novamall.common.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
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
        // Browsing is public; cart, addresses, orders and all writes still require identity.
        if (exchange.getRequest().getMethod() == HttpMethod.GET && isCatalogRead(path)) {
            return chain.filter(exchange.mutate().request(request -> request.headers(headers -> {
                headers.remove("X-User-Id");
                headers.remove("X-User-Name");
                headers.remove("X-User-Role");
            })).build());
        }
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
        String role = jwtTokenService.role(token);
        if (requiresAdmin(exchange.getRequest().getMethod(), path)
                && !"ADMIN".equalsIgnoreCase(role)) {
            return forbidden(exchange);
        }
        ServerWebExchange authenticatedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Name");
                    headers.remove("X-User-Role");
                    if (userId != null) {
                        headers.add("X-User-Id", String.valueOf(userId));
                    }
                    headers.add("X-User-Name", username);
                    if (role != null && !role.isBlank()) {
                        headers.add("X-User-Role", role);
                    }
                }))
                .build();
        return chain.filter(authenticatedExchange);
    }

    private boolean isCatalogRead(String path) {
        return path.equals("/api/products") || path.equals("/api/categories")
                || path.equals("/api/coupons/campaigns") || path.matches("/api/reviews/[0-9]+")
                || path.equals("/api/products/sales-counts") || path.equals("/api/inventory/summary")
                || path.matches("/api/products/[0-9]+/(detail|skus)")
                || path.matches("/api/inventory/skus/[0-9]+");
    }

    private boolean requiresAdmin(HttpMethod method, String path) {
        if (method == null) return false;
        if (path.equals("/api/coupons/admin") || path.startsWith("/api/coupons/admin/")) return true;
        if (path.equals("/api/refunds/admin") || path.startsWith("/api/refunds/admin/")) return true;
        if (path.equals("/api/products") && method == HttpMethod.POST) return true;
        if (path.startsWith("/api/products/")
                && (method == HttpMethod.PUT || method == HttpMethod.DELETE)) return true;
        if (path.equals("/api/categories") && method == HttpMethod.POST) return true;
        if (path.startsWith("/api/categories/")
                && (method == HttpMethod.PUT || method == HttpMethod.DELETE)) return true;
        if (path.equals("/api/inventory") && method == HttpMethod.POST) return true;
        return method == HttpMethod.POST && path.startsWith("/api/seckill/")
                && path.endsWith("/prepare");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"success\":false,\"message\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"success\":false,\"code\":\"FORBIDDEN\",\"message\":\"admin role required\"}"
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
