package com.aveline.bizflow.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.performance")
public record PerformanceProperties(
        @Min(1) long slowRequestMs,
        @Min(1) long slowQueryMs
) {
}
