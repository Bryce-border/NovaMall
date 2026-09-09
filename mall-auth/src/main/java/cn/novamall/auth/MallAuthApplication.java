package cn.novamall.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import cn.novamall.common.web.ApiExceptionHandler;
import cn.novamall.common.web.TraceIdFeignInterceptor;
import cn.novamall.common.web.TraceIdFilter;

@SpringBootApplication
@EnableFeignClients
@Import({ApiExceptionHandler.class, TraceIdFilter.class, TraceIdFeignInterceptor.class})
public class MallAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallAuthApplication.class, args);
    }
}
