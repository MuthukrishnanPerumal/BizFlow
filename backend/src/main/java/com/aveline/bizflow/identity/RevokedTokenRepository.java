package com.aveline.bizflow.identity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    int deleteByExpiresAtBefore(Instant instant);
}
