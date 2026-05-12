package com.coderank.auth.service;

import com.coderank.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenCleanupServiceTest {

    @Mock RefreshTokenRepository refreshTokenRepository;
    @InjectMocks TokenCleanupService tokenCleanupService;

    @Test
    void purgeExpiredRefreshTokens_shouldCallRepositoryWithYesterdayCutoff() {
        tokenCleanupService.purgeExpiredRefreshTokens();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(refreshTokenRepository).deleteAllExpiredBefore(captor.capture());

        Instant cutoff = captor.getValue();
        Instant expectedCutoff = Instant.now().minus(1, ChronoUnit.DAYS);
        assertThat(cutoff).isBetween(
                expectedCutoff.minusSeconds(5),
                expectedCutoff.plusSeconds(5)
        );
    }
}