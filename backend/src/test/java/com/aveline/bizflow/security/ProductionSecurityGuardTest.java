package com.aveline.bizflow.security;

import com.aveline.bizflow.config.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSecurityGuardTest {

    @Test
    void rejectsTheDevelopmentSecret() {
        ProductionSecurityGuard guard = guard(
                "dev-only-change-me-use-32-characters-min",
                "https://app.example.com",
                "database-password");

        assertThrows(IllegalStateException.class, () -> guard.run(new DefaultApplicationArguments()));
    }

    @Test
    void rejectsARepeatedCharacterSecret() {
        ProductionSecurityGuard guard = guard(
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "https://app.example.com",
                "database-password");

        assertThrows(IllegalStateException.class, () -> guard.run(new DefaultApplicationArguments()));
    }

    @Test
    void rejectsWildcardOriginsAndABlankDatabasePassword() {
        assertThrows(IllegalStateException.class, () -> guard(
                "production-secret-value-9f3c2a",
                "*",
                "database-password").run(new DefaultApplicationArguments()));
        assertThrows(IllegalStateException.class, () -> guard(
                "production-secret-value-9f3c2a",
                "https://app.example.com",
                " ").run(new DefaultApplicationArguments()));
    }

    @Test
    void acceptsExplicitProductionSettings() {
        ProductionSecurityGuard guard = guard(
                "production-secret-value-9f3c2a",
                "https://app.example.com",
                "database-password");

        assertDoesNotThrow(() -> guard.run(new DefaultApplicationArguments()));
    }

    private ProductionSecurityGuard guard(String secret, String origins, String password) {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.datasource.password", password);
        return new ProductionSecurityGuard(
                new JwtProperties(secret, Duration.ofHours(8)),
                new CorsProperties(origins),
                environment);
    }
}
