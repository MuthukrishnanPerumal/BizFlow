package com.aveline.bizflow.security;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.security.login")
public record LoginThrottleProperties(
        @Min(1) @Max(100_000) int maxAttempts,
        @NotNull Duration window
) {
}
