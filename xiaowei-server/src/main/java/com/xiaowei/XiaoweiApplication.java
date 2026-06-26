package com.xiaowei;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class XiaoweiApplication {

    public static void main(String[] args) {
        SpringApplication.run(XiaoweiApplication.class, args);
    }
}
