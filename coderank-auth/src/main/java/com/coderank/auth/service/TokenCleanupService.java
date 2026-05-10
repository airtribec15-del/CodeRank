package com.coderank.auth.service;

import com.coderank.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeExpiredRefreshTokens() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.DAYS);
        refreshTokenRepository.deleteAllExpiredBefore(cutoff);
        log.info("Purged expired refresh tokens older than cutoff: {}", cutoff);
    }
}