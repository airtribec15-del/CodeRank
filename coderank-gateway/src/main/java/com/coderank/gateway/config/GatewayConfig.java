package com.coderank.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

@Configuration
public class GatewayConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    /**
     * @Primary — this is the DEFAULT KeyResolver injected by GatewayAutoConfiguration
     * into RequestRateLimiterGatewayFilterFactory. Used for public/auth routes (IP-based).
     */
    @Bean
    @Primary                          // <-- THIS IS THE FIX
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                Objects.requireNonNull(exchange.getRequest().getRemoteAddress())
                        .getAddress().getHostAddress()
        );
    }

    /**
     * Secondary resolver — referenced BY NAME in application.yml for authenticated routes:
     * key-resolver: "#{@userKeyResolver}"
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String authHeader = exchange.getRequest()
                    .getHeaders()
                    .getFirst("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return Mono.just(authHeader.substring(7));
            }
            return Mono.just(
                    Objects.requireNonNull(exchange.getRequest().getRemoteAddress())
                            .getAddress().getHostAddress()
            );
        };
    }
}