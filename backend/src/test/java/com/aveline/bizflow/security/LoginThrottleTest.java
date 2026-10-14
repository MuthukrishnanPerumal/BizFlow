package com.aveline.bizflow.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginThrottleTest {

    @Test
    void blocksOneAddressWithoutBlockingAnother() {
        LoginThrottle throttle = new LoginThrottle(2, Duration.ofMinutes(1), Clock.systemUTC());

        assertTrue(throttle.allow("10.0.0.1"));
        assertTrue(throttle.allow("10.0.0.1"));
        assertFalse(throttle.allow("10.0.0.1"));
        assertTrue(throttle.allow("10.0.0.2"));
    }

    @Test
    void opensANewWindowAfterTheLimitExpires() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-03T18:00:00Z"));
        LoginThrottle throttle = new LoginThrottle(1, Duration.ofMinutes(1), new FixedMutableClock(now));

        assertTrue(throttle.allow("10.0.0.8"));
        assertFalse(throttle.allow("10.0.0.8"));
        now.set(now.get().plus(Duration.ofMinutes(1)));
        assertTrue(throttle.allow("10.0.0.8"));
    }

    private static final class FixedMutableClock extends Clock {
        private final AtomicReference<Instant> now;

        private FixedMutableClock(AtomicReference<Instant> now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
