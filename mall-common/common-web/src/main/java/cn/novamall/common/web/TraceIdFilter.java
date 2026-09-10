package cn.novamall.common.web;

import cn.novamall.common.core.TraceIdContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = TraceIdContext.resolve(request.getHeader(TraceIdContext.HEADER_NAME));
        response.setHeader(TraceIdContext.HEADER_NAME, traceId);
        TraceIdContext.setCurrent(traceId);
        MDC.put(TraceIdContext.MDC_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceIdContext.MDC_KEY);
            TraceIdContext.clearCurrent();
        }
    }
}
