package com.coderank.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/auth")
    public Mono<ResponseEntity<Map<String, Object>>> authFallback() {
        log.warn("Circuit breaker triggered for Auth Service");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", 503,
                "error", "SERVICE_UNAVAILABLE",
                "message", "Auth Service is currently unavailable. Please try again shortly.",
                "timestamp", Instant.now().toString()
        )));
    }

    @RequestMapping("/submission")
    public Mono<ResponseEntity<Map<String, Object>>> submissionFallback() {
        log.warn("Circuit breaker triggered for Submission Service");
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", 503,
                "error", "SERVICE_UNAVAILABLE",
                "message", "Submission Service is currently unavailable. Please try again shortly.",
                "timestamp", Instant.now().toString()
        )));
    }
}