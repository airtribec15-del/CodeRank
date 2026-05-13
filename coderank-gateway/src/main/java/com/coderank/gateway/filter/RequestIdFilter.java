package com.coderank.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    // Runs first — before JWT (-2) so all requests get a request ID including rejected ones
    private static final int ORDER = -3;

    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Honour existing X-Request-ID if the client sent one, else generate a new UUID
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        final String finalRequestId = requestId;

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, finalRequestId)
                .build();

        // Also expose X-Request-ID on the response so clients can correlate logs
        exchange.getResponse().getHeaders().add(REQUEST_ID_HEADER, finalRequestId);

        log.debug("Request ID assigned: {} → {}", finalRequestId,
                exchange.getRequest().getPath().value());

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
}