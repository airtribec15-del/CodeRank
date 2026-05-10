package com.coderank.auth.service;

import com.coderank.auth.security.JwtTokenProvider;
import com.coderank.common.constants.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public void logout(String accessToken, String rawRefreshToken) {
        String jti = jwtTokenProvider.extractJti(accessToken);
        long remainingMs = jwtTokenProvider.getRemainingValidityMs(accessToken);

        if (remainingMs > 0) {
            redisTemplate.opsForValue().set(
                    RedisKeys.jwtBlacklistKey(jti),
                    "1",
                    remainingMs,
                    TimeUnit.MILLISECONDS
            );
            log.debug("Blacklisted JWT jti: {} for {}ms", jti, remainingMs);
        }

        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revokeByRawToken(rawRefreshToken);
        }

        log.info("User logged out, jti blacklisted: {}", jti);
    }
}