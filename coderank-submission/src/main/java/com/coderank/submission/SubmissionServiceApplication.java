package com.coderank.submission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SubmissionServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SubmissionServiceApplication.class, args);
    }
}
