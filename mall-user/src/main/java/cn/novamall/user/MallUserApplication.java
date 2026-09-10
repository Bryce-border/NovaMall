package cn.novamall.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Import;
import org.springframework.cloud.openfeign.EnableFeignClients;
import cn.novamall.common.web.ApiExceptionHandler;
import cn.novamall.common.web.TraceIdFilter;
import cn.novamall.common.web.TraceIdFeignInterceptor;

@SpringBootApplication
@MapperScan("cn.novamall.user.mapper")
@EnableFeignClients
@Import({ApiExceptionHandler.class, TraceIdFilter.class, TraceIdFeignInterceptor.class})
public class MallUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallUserApplication.class, args);
    }
}
