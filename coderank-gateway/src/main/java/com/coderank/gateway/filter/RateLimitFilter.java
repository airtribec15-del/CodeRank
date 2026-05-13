package com.coderank.gateway.filter;

import com.coderank.common.constants.RedisKeys;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    // Token Bucket config — 20 requests per 60-second window
    private static final int CAPACITY = 20;
    private static final Duration WINDOW = Duration.ofSeconds(60);

    // Order: runs after JWT (-2) but before routing (0)
    private static final int ORDER = -1;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String bucketKey = resolveBucketKey(exchange);

        return redisTemplate.opsForValue()
                .increment(bucketKey)
                .flatMap(count -> {
                    // On first increment, set the TTL window
                    if (count == 1) {
                        return redisTemplate.expire(bucketKey, WINDOW)
                                .thenReturn(count);
                    }
                    return Mono.just(count);
                })
                .flatMap(count -> {
                    // Attach rate limit headers to response regardless of outcome
                    long remaining = Math.max(0, CAPACITY - count);
                    exchange.getResponse().getHeaders()
                            .add("X-RateLimit-Limit", String.valueOf(CAPACITY));
                    exchange.getResponse().getHeaders()
                            .add("X-RateLimit-Remaining", String.valueOf(remaining));

                    if (count > CAPACITY) {
                        log.warn("Rate limit exceeded for key: {}", bucketKey);
                        return writeRateLimitResponse(exchange);
                    }
                    return chain.filter(exchange);
                });
    }

    /**
     * Authenticated requests are bucketed per userId (injected by JwtValidationFilter).
     * Unauthenticated requests (auth routes) are bucketed per remote IP.
     */
    private String resolveBucketKey(ServerWebExchange exchange) {
        String userId = (String) exchange.getAttributes().get("authenticatedUserId");
        if (userId != null) {
            return RedisKeys.rateLimitUserKey(userId);
        }
        String ip = resolveClientIp(exchange);
        return RedisKeys.rateLimitIpKey(ip);
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders()
                .getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        var remoteAddress = exchange.getRequest().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
    }

    private Mono<Void> writeRateLimitResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().add("Retry-After", String.valueOf(WINDOW.getSeconds()));

        Map<String, Object> body = Map.of(
                "status", 429,
                "error", "RATE_LIMIT_EXCEEDED",
                "message", "Too many requests. Please retry after 60 seconds.",
                "timestamp", Instant.now().toString()
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = "{\"error\":\"RATE_LIMIT_EXCEEDED\"}".getBytes();
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}