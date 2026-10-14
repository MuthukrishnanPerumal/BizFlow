package com.aveline.bizflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey key;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(String username, List<String> roles) {
        return generateAccessToken(username, roles, List.of(), 0);
    }

    public String generateAccessToken(
            String username,
            List<String> roles,
            List<String> permissions,
            int sessionVersion) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (sessionVersion < 0) {
            throw new IllegalArgumentException("sessionVersion must be zero or greater");
        }
        List<String> safeRoles = roles == null ? List.of() : List.copyOf(roles);
        List<String> safePermissions = permissions == null ? List.of() : List.copyOf(permissions);
        Date issuedAt = Date.from(clock.instant());
        Date expiresAt = Date.from(clock.instant().plus(properties.accessTokenTtl()));
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim("roles", safeRoles)
                .claim("permissions", safePermissions)
                .claim("sv", sessionVersion)
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(key)
                .compact();
    }

    public JwtPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Token subject is missing");
        }
        String tokenId = claims.getId();
        if (tokenId == null || tokenId.isBlank()) {
            throw new IllegalArgumentException("Token identifier is missing");
        }
        if (claims.getExpiration() == null) {
            throw new IllegalArgumentException("Token expiration is missing");
        }
        return new JwtPrincipal(
                subject,
                readStrings(claims.get("roles")),
                readStrings(claims.get("permissions")),
                tokenId,
                readVersion(claims.get("sv")),
                claims.getExpiration().toInstant());
    }

    private List<String> readStrings(Object raw) {
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    private int readVersion(Object raw) {
        if (raw instanceof Number number) {
            return Math.max(number.intValue(), 0);
        }
        return 0;
    }
}
