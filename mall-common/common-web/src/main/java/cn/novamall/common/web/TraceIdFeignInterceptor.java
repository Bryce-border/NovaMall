package cn.novamall.common.web;

import cn.novamall.common.core.TraceIdContext;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;

/** Propagates the current request trace ID through service-to-service Feign calls. */
public class TraceIdFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String traceId = MDC.get(TraceIdContext.MDC_KEY);
        if (traceId != null && !traceId.isBlank()) {
            template.header(TraceIdContext.HEADER_NAME, traceId);
        }
    }
}
