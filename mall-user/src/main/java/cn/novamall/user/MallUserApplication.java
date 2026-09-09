package cn.novamall.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Import;
import cn.novamall.common.web.ApiExceptionHandler;
import cn.novamall.common.web.TraceIdFilter;

@SpringBootApplication
@MapperScan("cn.novamall.user.mapper")
@Import({ApiExceptionHandler.class, TraceIdFilter.class})
public class MallUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallUserApplication.class, args);
    }
}
