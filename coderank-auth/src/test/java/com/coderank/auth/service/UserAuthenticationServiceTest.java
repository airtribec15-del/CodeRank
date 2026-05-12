package com.coderank.auth.service;

import com.coderank.auth.dto.LoginRequest;
import com.coderank.auth.dto.TokenResponse;
import com.coderank.auth.entity.User;
import com.coderank.auth.repository.UserRepository;
import com.coderank.auth.security.JwtTokenProvider;
import com.coderank.common.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAuthenticationServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RefreshTokenService refreshTokenService;

    @InjectMocks UserAuthenticationService authenticationService;

    private User buildUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .username("alice")
                .email("alice@example.com")
                .passwordHash("hashedPassword")
                .role("ROLE_USER")
                .build();
    }

    @Test
    void login_shouldReturnTokenResponse_whenCredentialsValid() {
        User user = buildUser();
        LoginRequest request = new LoginRequest("alice@example.com", "password123");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(anyString(), eq("ROLE_USER")))
                .thenReturn("mock-access-token");
        when(refreshTokenService.createRefreshToken(any(User.class), anyString()))
                .thenReturn("mock-refresh-token");

        TokenResponse response = authenticationService.login(request, "Mozilla/5.0");

        assertThat(response.getAccessToken()).isEqualTo("mock-access-token");
        assertThat(response.getUsername()).isEqualTo("alice");
    }

    @Test
    void login_shouldThrow_whenEmailNotFound() {
        LoginRequest request = new LoginRequest("unknown@example.com", "password123");
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.login(request, "agent"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void login_shouldThrow_whenPasswordDoesNotMatch() {
        User user = buildUser();
        LoginRequest request = new LoginRequest("alice@example.com", "wrongpass");

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.login(request, "agent"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid email or password");
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any());
    }
}