package com.coderank.auth.service;

import com.coderank.auth.dto.RegisterRequest;
import com.coderank.auth.dto.TokenResponse;
import com.coderank.auth.entity.User;
import com.coderank.auth.repository.UserRepository;
import com.coderank.auth.security.JwtTokenProvider;
import com.coderank.common.exception.InvalidRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public TokenResponse register(RegisterRequest request, String deviceInfo) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new InvalidRequestException("Email already registered");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new InvalidRequestException("Username already taken");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role("ROLE_USER")
                .build();

        User savedUser = userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        String accessToken = jwtTokenProvider.generateAccessToken(savedUser.getId().toString(), savedUser.getRole());
        String refreshToken = refreshTokenService.createRefreshToken(savedUser, deviceInfo);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(14400)
                .userId(savedUser.getId())
                .username(savedUser.getUsername())
                .role(savedUser.getRole())
                .build();
    }
}