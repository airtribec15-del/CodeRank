package com.coderank.auth.service;

import com.coderank.auth.dto.LoginRequest;
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
public class UserAuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public TokenResponse login(LoginRequest request, String deviceInfo) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidRequestException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidRequestException("Invalid email or password");
        }

        log.info("User authenticated: {}", user.getEmail());

        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId().toString(), user.getRole());
        String refreshToken = refreshTokenService.createRefreshToken(user, deviceInfo);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(14400)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }
}