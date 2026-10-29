package com;

import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 智枢学堂启动类
 */
@SpringBootApplication
@MapperScan("com.mapper")
@EnableAsync
@EnableCaching
public class SpringBoot {

    private static final Logger log = LoggerFactory.getLogger(SpringBoot.class);

    public static void main(String[] args) {
        SpringApplication.run(SpringBoot.class, args);
        // 走日志框架而非 stdout，容器化部署时才能被日志采集拿到
        log.info("智枢学堂启动成功");
    }
}
