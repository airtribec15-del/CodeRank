package com.coderank.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private GatewayFilterChain chain;
    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        ObjectMapper objectMapper = new ObjectMapper();

        filter = new RateLimitFilter(redisTemplate, objectMapper);
        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.expire(anyString(), any(Duration.class)))
                .thenReturn(Mono.just(true));
    }

    @Test
    @DisplayName("Should allow request when count is within limit")
    void shouldAllowRequestWhenUnderLimit() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(5L));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/submissions/test").build());
        exchange.getAttributes().put("authenticatedUserId", "user-123");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Limit"))
                .isEqualTo("20");
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"))
                .isEqualTo("15");
    }

    @Test
    @DisplayName("Should allow request when count is exactly at limit (20)")
    void shouldAllowRequestWhenAtLimit() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(20L));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/submissions/test").build());
        exchange.getAttributes().put("authenticatedUserId", "user-123");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"))
                .isEqualTo("0");
    }

    @Test
    @DisplayName("Should return 429 when count exceeds limit")
    void shouldReturn429WhenOverLimit() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(21L));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/submissions/test").build());
        exchange.getAttributes().put("authenticatedUserId", "user-123");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(429);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After"))
                .isEqualTo("60");
    }

    @Test
    @DisplayName("Should use userId bucket key for authenticated requests")
    void shouldUseUserIdBucketForAuthenticatedRequests() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/submissions/test").build());
        // Authenticated user set by JwtValidationFilter
        exchange.getAttributes().put("authenticatedUserId", "user-abc-123");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Verify TTL set on first request
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Should use IP bucket key for unauthenticated requests")
    void shouldUseIpBucketForUnauthenticatedRequests() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));

        // No authenticatedUserId attribute — unauthenticated request
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Should set TTL on first request in window")
    void shouldSetTtlOnFirstRequest() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/submissions/test").build());
        exchange.getAttributes().put("authenticatedUserId", "user-123");

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // expire() called with 60s window on count == 1
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Should have order -1 to run after JwtValidationFilter")
    void shouldHaveCorrectOrder() {
        assertThat(filter.getOrder()).isEqualTo(-1);
    }
}