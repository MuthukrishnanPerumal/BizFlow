package com.aveline.bizflow.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "revoked_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevokedToken {

    @Id
    @Column(nullable = false, length = 64)
    private String jti;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(name = "expires_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant expiresAt;

    @Column(name = "revoked_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant revokedAt;

    public static RevokedToken revoke(String jti, String username, Instant expiresAt, Instant revokedAt) {
        RevokedToken token = new RevokedToken();
        token.jti = jti;
        token.username = username;
        token.expiresAt = expiresAt;
        token.revokedAt = revokedAt;
        return token;
    }
}
