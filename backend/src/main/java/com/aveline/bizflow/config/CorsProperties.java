package com.aveline.bizflow.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(@NotBlank String allowedOrigins) {

    public List<String> origins() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();
    }
}
