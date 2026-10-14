package com.aveline.bizflow.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "dev-only-change-me-use-32-characters-min";

    @Test
    void generatesAndParsesAccessToken() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
        JwtService service = new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(15)), clock);

        String token = service.generateAccessToken("asha", List.of("ADMIN", "AUDITOR"));
        JwtPrincipal principal = service.parse(token);

        assertEquals("asha", principal.getUsername());
        assertEquals(List.of("ADMIN", "AUDITOR"), principal.roles());
        assertTrue(principal.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void rejectsExpiredToken() {
        Clock issued = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
        JwtService issuer = new JwtService(new JwtProperties(SECRET, Duration.ofSeconds(5)), issued);
        String token = issuer.generateAccessToken("asha", List.of());

        Clock later = Clock.fixed(Instant.parse("2026-10-01T00:10:00Z"), ZoneOffset.UTC);
        JwtService parser = new JwtService(new JwtProperties(SECRET, Duration.ofSeconds(5)), later);

        assertThrows(ExpiredJwtException.class, () -> parser.parse(token));
    }

    @Test
    void rejectsTamperedToken() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
        JwtService service = new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(15)), clock);
        String token = service.generateAccessToken("asha", List.of("ADMIN"));
        String tampered = token.substring(0, token.length() - 2) + "aa";

        assertThrows(JwtException.class, () -> service.parse(tampered));
    }

    @Test
    void rejectsBlankSubject() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
        JwtService service = new JwtService(new JwtProperties(SECRET, Duration.ofMinutes(15)), clock);

        assertThrows(IllegalArgumentException.class, () -> service.generateAccessToken(" ", List.of()));
    }
}
