package cn.novamall.product;

import cn.novamall.common.web.ApiExceptionHandler;
import cn.novamall.common.web.TraceIdFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@MapperScan("cn.novamall.product.mapper")
@Import({ApiExceptionHandler.class, TraceIdFilter.class})
public class MallProductApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallProductApplication.class, args);
    }
}
