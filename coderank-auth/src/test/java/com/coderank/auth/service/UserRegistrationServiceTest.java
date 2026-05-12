package com.coderank.auth.service;

import com.coderank.auth.dto.RegisterRequest;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RefreshTokenService refreshTokenService;

    @InjectMocks UserRegistrationService registrationService;

    @Test
    void register_shouldReturnTokenResponse_whenValidRequest() {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123");

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashedPassword");

        // ✅ FIX: return a user WITH a pre-set UUID — JPA won't generate it in a mock
        User savedUser = User.builder()
                .id(UUID.randomUUID())          // <-- this was missing or null before
                .username("alice")
                .email("alice@example.com")
                .passwordHash("hashedPassword")
                .role("ROLE_USER")
                .build();

        // ✅ FIX: answer with the savedUser (which has an ID), not the input object
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        when(jwtTokenProvider.generateAccessToken(anyString(), eq("ROLE_USER")))
                .thenReturn("mock-access-token");
        when(refreshTokenService.createRefreshToken(any(User.class), anyString()))
                .thenReturn("mock-refresh-token");

        TokenResponse response = registrationService.register(request, "Mozilla/5.0");

        assertThat(response.getAccessToken()).isEqualTo("mock-access-token");
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getRole()).isEqualTo("ROLE_USER");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_shouldThrow_whenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123");
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.register(request, "agent"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Email already registered");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldThrow_whenUsernameAlreadyTaken() {
        RegisterRequest request = new RegisterRequest("alice", "alice@example.com", "password123");
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> registrationService.register(request, "agent"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Username already taken");
        verify(userRepository, never()).save(any());
    }
}