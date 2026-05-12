package com.coderank.auth.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    // 64+ char secret to satisfy HS256 key length requirement
    private static final String SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET);
    }

    @Test
    void generateAccessToken_shouldReturnNonNullToken() {
        String userId = UUID.randomUUID().toString();
        String token = jwtTokenProvider.generateAccessToken(userId, "ROLE_USER");
        assertThat(token).isNotBlank();
    }

    @Test
    void validateAndExtractClaims_shouldExtractCorrectSubject() {
        String userId = UUID.randomUUID().toString();
        String token = jwtTokenProvider.generateAccessToken(userId, "ROLE_USER");

        Claims claims = jwtTokenProvider.validateAndExtractClaims(token);

        assertThat(claims.getSubject()).isEqualTo(userId);
    }

    @Test
    void validateAndExtractClaims_shouldExtractCorrectRole() {
        String userId = UUID.randomUUID().toString();
        String token = jwtTokenProvider.generateAccessToken(userId, "ROLE_ADMIN");

        Claims claims = jwtTokenProvider.validateAndExtractClaims(token);

        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void extractUserId_shouldMatchGeneratedUserId() {
        String userId = UUID.randomUUID().toString();
        String token = jwtTokenProvider.generateAccessToken(userId, "ROLE_USER");

        assertThat(jwtTokenProvider.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    void extractJti_shouldReturnNonBlankJti() {
        String token = jwtTokenProvider.generateAccessToken(UUID.randomUUID().toString(), "ROLE_USER");
        assertThat(jwtTokenProvider.extractJti(token)).isNotBlank();
    }

    @Test
    void isValid_shouldReturnTrueForValidToken() {
        String token = jwtTokenProvider.generateAccessToken(UUID.randomUUID().toString(), "ROLE_USER");
        assertThat(jwtTokenProvider.isValid(token)).isTrue();
    }

    @Test
    void isValid_shouldReturnFalseForGarbageToken() {
        assertThat(jwtTokenProvider.isValid("not.a.real.token")).isFalse();
    }

    @Test
    void getRemainingValidityMs_shouldBePositiveForFreshToken() {
        String token = jwtTokenProvider.generateAccessToken(UUID.randomUUID().toString(), "ROLE_USER");
        assertThat(jwtTokenProvider.getRemainingValidityMs(token)).isPositive();
    }

    @Test
    void getRemainingValidityMs_shouldBeCloseTo4Hours() {
        String token = jwtTokenProvider.generateAccessToken(UUID.randomUUID().toString(), "ROLE_USER");
        long remaining = jwtTokenProvider.getRemainingValidityMs(token);
        long fourHoursMs = 4L * 60 * 60 * 1000;
        // Allow 5-second tolerance
        assertThat(remaining).isBetween(fourHoursMs - 5000, fourHoursMs);
    }

    @Test
    void getSecretKey_shouldNotBeNull() {
        assertThat(jwtTokenProvider.getSecretKey()).isNotNull();
    }
}