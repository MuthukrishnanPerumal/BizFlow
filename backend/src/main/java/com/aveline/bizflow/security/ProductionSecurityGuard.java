package com.aveline.bizflow.security;

import com.aveline.bizflow.config.CorsProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
@Profile("prod")
public class ProductionSecurityGuard implements ApplicationRunner {

    static final Set<String> FORBIDDEN_SECRETS = Set.of(
            "dev-only-change-me-use-32-characters-min",
            "test-only-change-me-use-32-characters-min");

    private final JwtProperties jwtProperties;
    private final CorsProperties corsProperties;
    private final Environment environment;

    public ProductionSecurityGuard(
            JwtProperties jwtProperties,
            CorsProperties corsProperties,
            Environment environment) {
        this.jwtProperties = jwtProperties;
        this.corsProperties = corsProperties;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        assertSecret();
        assertCors();
        assertDatabasePassword();
    }

    private void assertSecret() {
        String secret = jwtProperties.secret();
        String normalized = secret.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_SECRETS.contains(secret)
                || normalized.contains("change-me")
                || secret.chars().distinct().count() < 8) {
            throw new IllegalStateException("JWT_SECRET is not acceptable for production");
        }
    }

    private void assertCors() {
        if (corsProperties.origins().isEmpty()
                || corsProperties.origins().stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("CORS_ALLOWED_ORIGINS must list explicit origins");
        }
    }

    private void assertDatabasePassword() {
        String password = environment.getProperty("spring.datasource.password");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("DB_PASSWORD is required in production");
        }
    }
}
