package com.aveline.bizflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.api-docs")
public record ApiDocsProperties(boolean enabled) {
}
