package com.aveline.bizflow.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginThrottle {

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    @Autowired
    public LoginThrottle(LoginThrottleProperties properties, Clock clock) {
        this(properties.maxAttempts(), properties.window(), clock);
    }

    LoginThrottle(int maxAttempts, Duration window, Clock clock) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    public boolean allow(String key) {
        String safeKey = key == null || key.isBlank() ? "unknown" : key;
        Instant now = clock.instant();
        prune(now);
        AttemptWindow current = attempts.compute(safeKey, (ignored, existing) -> {
            if (existing == null || !existing.started.plus(window).isAfter(now)) {
                return new AttemptWindow(now);
            }
            existing.count++;
            return existing;
        });
        return current.count <= maxAttempts;
    }

    public long retryAfterSeconds() {
        return Math.max(window.toSeconds(), 1);
    }

    private void prune(Instant now) {
        if (attempts.size() < 4096) {
            return;
        }
        attempts.entrySet().removeIf(entry -> !entry.getValue().started.plus(window).isAfter(now));
    }

    private static final class AttemptWindow {
        private final Instant started;
        private int count;

        private AttemptWindow(Instant started) {
            this.started = started;
            this.count = 1;
        }
    }
}
