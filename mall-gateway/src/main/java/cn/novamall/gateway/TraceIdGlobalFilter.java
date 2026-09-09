package cn.novamall.gateway;

import cn.novamall.common.core.TraceIdContext;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = TraceIdContext.resolve(
                exchange.getRequest().getHeaders().getFirst(TraceIdContext.HEADER_NAME));
        ServerWebExchange tracedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(TraceIdContext.HEADER_NAME);
                    headers.add(TraceIdContext.HEADER_NAME, traceId);
                }))
                .build();
        tracedExchange.getResponse().beforeCommit(() -> {
            var headers = tracedExchange.getResponse().getHeaders();
            headers.remove(TraceIdContext.HEADER_NAME);
            headers.set(TraceIdContext.HEADER_NAME, traceId);
            return Mono.empty();
        });
        return chain.filter(tracedExchange);
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
