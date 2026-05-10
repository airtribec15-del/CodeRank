package com.coderank.auth.service;

import com.coderank.auth.dto.TokenResponse;
import com.coderank.auth.entity.RefreshToken;
import com.coderank.auth.entity.User;
import com.coderank.auth.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRefreshService {

    private final RefreshTokenService refreshTokenService;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public TokenResponse refresh(String rawRefreshToken, String deviceInfo) {
        RefreshToken rotated = refreshTokenService.validateAndRotate(rawRefreshToken, deviceInfo);
        User user = rotated.getUser();

        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId().toString(), user.getRole());
        String newRawRefreshToken = rotated.getTokenHash();

        log.debug("Tokens refreshed for user: {}", user.getId());

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresIn(14400)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }
}