package cn.novamall.inventory;

import cn.novamall.common.web.ApiExceptionHandler;
import cn.novamall.common.web.TraceIdFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@MapperScan("cn.novamall.inventory.mapper")
@Import({ApiExceptionHandler.class, TraceIdFilter.class})
public class MallInventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallInventoryApplication.class, args);
    }
}
