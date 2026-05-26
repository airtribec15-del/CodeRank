package com.coderank.resultprocessor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ResultProcessorApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResultProcessorApplication.class, args);
    }
}