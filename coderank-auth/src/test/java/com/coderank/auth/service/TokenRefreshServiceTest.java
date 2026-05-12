package com.coderank.auth.service;

import com.coderank.auth.dto.TokenResponse;
import com.coderank.auth.entity.RefreshToken;
import com.coderank.auth.entity.User;
import com.coderank.auth.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenRefreshServiceTest {

    @Mock RefreshTokenService refreshTokenService;
    @Mock JwtTokenProvider jwtTokenProvider;

    @InjectMocks TokenRefreshService tokenRefreshService;

    @Test
    void refresh_shouldReturnNewTokenResponse() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("alice")
                .email("alice@example.com")
                .role("ROLE_USER")
                .build();

        // NOTE: validateAndRotate sets tokenHash to the new raw token in the codebase
        RefreshToken rotatedToken = RefreshToken.builder()
                .user(user)
                .tokenHash("new-raw-refresh-token") // set by service post-save
                .build();

        when(refreshTokenService.validateAndRotate(anyString(), anyString()))
                .thenReturn(rotatedToken);
        when(jwtTokenProvider.generateAccessToken(anyString(), eq("ROLE_USER")))
                .thenReturn("new-access-token");

        TokenResponse response = tokenRefreshService.refresh("old-raw-token", "Mozilla/5.0");

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(14400);
        assertThat(response.getUsername()).isEqualTo("alice");
    }
}