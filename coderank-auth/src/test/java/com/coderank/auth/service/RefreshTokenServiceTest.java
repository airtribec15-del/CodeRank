package com.coderank.auth.service;

import com.coderank.auth.entity.RefreshToken;
import com.coderank.auth.entity.User;
import com.coderank.auth.repository.RefreshTokenRepository;
import com.coderank.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @InjectMocks RefreshTokenService refreshTokenService;

    private User buildUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .username("alice")
                .email("alice@example.com")
                .passwordHash("hash")
                .role("ROLE_USER")
                .build();
    }

    @Test
    void createRefreshToken_shouldSaveAndReturnRawToken() {
        User user = buildUser();
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = refreshTokenService.createRefreshToken(user, "Mozilla/5.0");

        assertThat(rawToken).isNotBlank();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void validateAndRotate_shouldReturnNewRefreshToken_whenValid() {
        User user = buildUser();
        // We need a real rawToken to hash correctly
        String rawToken = UUID.randomUUID().toString();

        // Compute the hash the same way the service does
        String tokenHash = sha256Hex(rawToken);

        RefreshToken existing = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(false)
                .deviceInfo("Mozilla/5.0")
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RefreshToken rotated = refreshTokenService.validateAndRotate(rawToken, "Mozilla/5.0");

        assertThat(rotated).isNotNull();
        verify(refreshTokenRepository).delete(existing);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void validateAndRotate_shouldThrow_whenTokenNotFound() {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256Hex(rawToken);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.validateAndRotate(rawToken, "agent"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void validateAndRotate_shouldThrow_andRevokeAllTokens_whenTokenRevoked() {
        User user = buildUser();
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256Hex(rawToken);

        RefreshToken revoked = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.validateAndRotate(rawToken, "agent"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("revoked");
        verify(refreshTokenRepository).deleteAllByUserId(user.getId());
    }

    @Test
    void validateAndRotate_shouldThrow_whenTokenExpired() {
        User user = buildUser();
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256Hex(rawToken);

        RefreshToken expired = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)) // expired
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.validateAndRotate(rawToken, "agent"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void revokeByRawToken_shouldDeleteToken_whenExists() {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256Hex(rawToken);
        User user = buildUser();

        RefreshToken token = RefreshToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash(tokenHash)
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

        refreshTokenService.revokeByRawToken(rawToken);

        verify(refreshTokenRepository).delete(token);
    }

    @Test
    void revokeByRawToken_shouldDoNothing_whenTokenNotFound() {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256Hex(rawToken);

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(() -> refreshTokenService.revokeByRawToken(rawToken));
        verify(refreshTokenRepository, never()).delete(any());
    }

    // Mirror the service's private hashing logic
    private String sha256Hex(String input) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}