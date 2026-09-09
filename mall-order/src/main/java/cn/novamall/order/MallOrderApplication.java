package cn.novamall.order;

import cn.novamall.common.web.ApiExceptionHandler;
import cn.novamall.common.web.TraceIdFeignInterceptor;
import cn.novamall.common.web.TraceIdFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@MapperScan("cn.novamall.order.mapper")
@EnableScheduling
@Import({ApiExceptionHandler.class, TraceIdFilter.class, TraceIdFeignInterceptor.class})
public class MallOrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallOrderApplication.class, args);
    }
}
