package com.coderank.gateway.filter;

import com.coderank.gateway.config.JwtProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtValidationFilterTest {

    // Must be 32+ chars for HS256
    private static final String SECRET =
            "test-secret-key-minimum-32-chars-long!!";

    private JwtValidationFilter filter;
    private GatewayFilterChain chain;
    private ReactiveStringRedisTemplate redisTemplate;
    private ReactiveValueOperations<String, String> valueOps;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(SECRET);

        redisTemplate = mock(ReactiveStringRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        ObjectMapper objectMapper = new ObjectMapper();

        filter = new JwtValidationFilter(jwtProperties, redisTemplate, objectMapper);
        filter.init();

        chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        secretKey = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

        // Default: token not blacklisted
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));
    }

    // ── Helper: build a valid JWT ─────────────────────────────────────────────

    private String buildValidToken(String userId, String role, String jti) {
        return Jwts.builder()
                .subject(userId)
                .id(jti)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 4 * 60 * 60 * 1000L))
                .signWith(secretKey)
                .compact();
    }

    private String buildExpiredToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .id(UUID.randomUUID().toString())
                .claim("role", "ROLE_USER")
                .issuedAt(new Date(System.currentTimeMillis() - 10000))
                .expiration(new Date(System.currentTimeMillis() - 5000)) // expired
                .signWith(secretKey)
                .compact();
    }

    // ── Public path bypass ────────────────────────────────────────────────────

    @Test
    @DisplayName("Should bypass JWT validation for /api/v1/auth/register")
    void shouldBypassValidationForRegister() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/register").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Should bypass JWT validation for /api/v1/auth/login")
    void shouldBypassValidationForLogin() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    @DisplayName("Should bypass JWT validation for /actuator/health")
    void shouldBypassValidationForActuatorHealth() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    // ── Missing / malformed token ─────────────────────────────────────────────

    @Test
    @DisplayName("Should return 401 when Authorization header is missing")
    void shouldReturn401WhenAuthHeaderMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/submissions/test").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("Should return 401 when Authorization header does not start with Bearer")
    void shouldReturn401WhenNotBearerToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/submissions/test")
                        .header("Authorization", "Basic dXNlcjpwYXNz")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    // ── Invalid / expired token ───────────────────────────────────────────────

    @Test
    @DisplayName("Should return 401 when token has invalid signature")
    void shouldReturn401WhenTokenHasInvalidSignature() {
        // Token signed with a different key
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "wrong-secret-key-minimum-32-chars-long!!".getBytes(StandardCharsets.UTF_8));
        String invalidToken = Jwts.builder()
                .subject("user-123")
                .id(UUID.randomUUID().toString())
                .claim("role", "ROLE_USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(wrongKey)
                .compact();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/submissions/test")
                        .header("Authorization", "Bearer " + invalidToken)
                        .build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("Should return 401 when token is expired")
    void shouldReturn401WhenTokenExpired() {
        String expiredToken = buildExpiredToken("user-123");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/submissions/test")
                        .header("Authorization", "Bearer " + expiredToken)
                        .build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    // ── Redis blacklist ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Should return 401 TOKEN_REVOKED when jti is blacklisted in Redis")
    void shouldReturn401WhenTokenBlacklisted() {
        String jti = UUID.randomUUID().toString();
        String token = buildValidToken("user-123", "ROLE_USER", jti);

        // Simulate Redis returning true for blacklist key
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(true));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/submissions/test")
                        .header("Authorization", "Bearer " + token)
                        .build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    // ── Valid token ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Should pass request and inject headers when token is valid")
    void shouldPassAndInjectHeadersForValidToken() {
        String userId = UUID.randomUUID().toString();
        String jti = UUID.randomUUID().toString();
        String token = buildValidToken(userId, "ROLE_USER", jti);

        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(false));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/submissions/test")
                        .header("Authorization", "Bearer " + token)
                        .build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        // Response status should be null (request passed through)
        assertThat(exchange.getResponse().getStatusCode()).isNull();
        // authenticatedUserId attribute must be set for rate limiter
        assertThat(exchange.getAttributes().get("authenticatedUserId"))
                .isEqualTo(userId);
    }

    @Test
    @DisplayName("Should have order -2 to run after RequestIdFilter")
    void shouldHaveCorrectOrder() {
        assertThat(filter.getOrder()).isEqualTo(-2);
    }

    // Helper to avoid import issue with any()
    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}