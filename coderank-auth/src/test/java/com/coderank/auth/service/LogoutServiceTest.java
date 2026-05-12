package com.coderank.auth.service;

import com.coderank.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RefreshTokenService refreshTokenService;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks LogoutService logoutService;

    @Test
    void logout_shouldBlacklistJtiAndRevokeRefreshToken() {
        String accessToken = "valid-access-token";
        String rawRefreshToken = "raw-refresh-token";
        String jti = "test-jti-uuid";

        when(jwtTokenProvider.extractJti(accessToken)).thenReturn(jti);
        when(jwtTokenProvider.getRemainingValidityMs(accessToken)).thenReturn(10000L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        logoutService.logout(accessToken, rawRefreshToken);

        verify(valueOps).set(contains(jti), eq("1"), eq(10000L), eq(TimeUnit.MILLISECONDS));
        verify(refreshTokenService).revokeByRawToken(rawRefreshToken);
    }

    @Test
    void logout_shouldNotBlacklistJti_whenRemainingTimeIsZero() {
        String accessToken = "expired-access-token";
        String jti = "test-jti-uuid";

        when(jwtTokenProvider.extractJti(accessToken)).thenReturn(jti);
        when(jwtTokenProvider.getRemainingValidityMs(accessToken)).thenReturn(0L);

        logoutService.logout(accessToken, null);

        verify(redisTemplate, never()).opsForValue();
        verify(refreshTokenService, never()).revokeByRawToken(any());
    }

    @Test
    void logout_shouldSkipRefreshTokenRevocation_whenRefreshTokenIsNull() {
        String accessToken = "valid-access-token";
        String jti = "jti-uuid";

        when(jwtTokenProvider.extractJti(accessToken)).thenReturn(jti);
        when(jwtTokenProvider.getRemainingValidityMs(accessToken)).thenReturn(5000L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        logoutService.logout(accessToken, null);

        verify(valueOps).set(any(), any(), anyLong(), any());
        verify(refreshTokenService, never()).revokeByRawToken(any());
    }

    @Test
    void logout_shouldSkipRefreshTokenRevocation_whenRefreshTokenIsBlank() {
        String accessToken = "valid-access-token";
        String jti = "jti-uuid";

        when(jwtTokenProvider.extractJti(accessToken)).thenReturn(jti);
        when(jwtTokenProvider.getRemainingValidityMs(accessToken)).thenReturn(5000L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        logoutService.logout(accessToken, "   ");

        verify(refreshTokenService, never()).revokeByRawToken(any());
    }
}