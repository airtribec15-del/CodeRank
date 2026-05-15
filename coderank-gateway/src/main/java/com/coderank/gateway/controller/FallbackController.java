package com.coderank.gateway.controller;

import com.coderank.common.dto.response.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
public class FallbackController {

    @RequestMapping("/fallback/auth")
    public Mono<ResponseEntity<ErrorResponse>> authFallback() {
        return Mono.just(buildFallbackResponse("Auth Service is currently unavailable. Please try again later."));
    }

    @RequestMapping("/fallback/problem")
    public Mono<ResponseEntity<ErrorResponse>> problemFallback() {
        return Mono.just(buildFallbackResponse("Problem Service is currently unavailable. Please try again later."));
    }

    @RequestMapping("/fallback/submission")
    public Mono<ResponseEntity<ErrorResponse>> submissionFallback() {
        return Mono.just(buildFallbackResponse("Submission Service is currently unavailable. Please try again later."));
    }

    private ResponseEntity<ErrorResponse> buildFallbackResponse(String message) {
        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("SERVICE_UNAVAILABLE")
                .message(message)
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}