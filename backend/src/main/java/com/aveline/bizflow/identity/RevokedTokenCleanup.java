package com.aveline.bizflow.identity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Component
@Profile("!test")
public class RevokedTokenCleanup {

    private final RevokedTokenRepository revokedTokenRepository;
    private final Clock clock;

    public RevokedTokenCleanup(RevokedTokenRepository revokedTokenRepository, Clock clock) {
        this.revokedTokenRepository = revokedTokenRepository;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${app.security.revoked-token-cleanup-ms:3600000}",
            initialDelayString = "${app.security.revoked-token-cleanup-ms:3600000}")
    @Transactional
    public void purgeExpired() {
        int removed = revokedTokenRepository.deleteByExpiresAtBefore(clock.instant());
        if (removed > 0) {
            log.info("Purged expired revoked sessions count={}", removed);
        }
    }
}
