package com.aveline.bizflow.system.service;

import com.aveline.bizflow.common.exception.ResourceNotFoundException;
import com.aveline.bizflow.system.SettingKeys;
import com.aveline.bizflow.system.dto.CommercialDefaultsResponse;
import com.aveline.bizflow.system.dto.SystemHealthResponse;
import com.aveline.bizflow.system.dto.SystemInfoResponse;
import com.aveline.bizflow.system.persistence.PlatformSetting;
import com.aveline.bizflow.system.persistence.PlatformSettingRepository;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.boot.info.BuildProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class SystemQueryService {

    private static final Duration PREFERENCES_TTL = Duration.ofSeconds(60);

    private static final List<String> PREFERENCE_KEYS = List.of(
            SettingKeys.DISPLAY_NAME,
            SettingKeys.CURRENCY,
            SettingKeys.LOCALE,
            SettingKeys.DATE_FORMAT,
            SettingKeys.DECIMAL_PRECISION,
            SettingKeys.ROUNDING_RULE,
            SettingKeys.DEFAULT_PAYMENT_METHOD);

    private final DataSource dataSource;
    private final Flyway flyway;
    private final PlatformSettingRepository platformSettingRepository;
    private final Environment environment;
    private final BuildProperties buildProperties;
    private final Clock clock;
    /** Flyway schema history is immutable at runtime; avoid flyway.info() on every health probe. */
    private final AtomicInteger cachedAppliedMigrations = new AtomicInteger(-1);
    private final AtomicReference<CachedPreferences> preferencesCache = new AtomicReference<>();

    public SystemQueryService(
            DataSource dataSource,
            Flyway flyway,
            PlatformSettingRepository platformSettingRepository,
            Environment environment,
            ObjectProvider<BuildProperties> buildProperties,
            Clock clock) {
        this.dataSource = dataSource;
        this.flyway = flyway;
        this.platformSettingRepository = platformSettingRepository;
        this.environment = environment;
        this.buildProperties = buildProperties.getIfAvailable();
        this.clock = clock;
    }

    public SystemInfoResponse info() {
        String version = buildProperties == null ? "dev" : buildProperties.getVersion();
        String[] profiles = environment.getActiveProfiles();
        String activeEnvironment = profiles.length == 0 ? "default" : String.join(",", profiles);
        return new SystemInfoResponse("Aveline BizFlow", version, activeEnvironment, "v1", clock.instant());
    }

    public SystemHealthResponse health() {
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            int migrations = valid ? appliedMigrations() : 0;
            String state = valid ? "UP" : "DOWN";
            return new SystemHealthResponse(state, state, migrations);
        } catch (Exception ex) {
            if (ex instanceof SQLException sqlException) {
                log.warn("Database health check failed sqlState={}", sqlException.getSQLState());
            } else {
                log.warn("Database health check failed ({})", ex.getClass().getSimpleName());
            }
            return new SystemHealthResponse("DOWN", "DOWN", 0);
        }
    }

    @Transactional(readOnly = true)
    public CommercialDefaultsResponse preferences() {
        long now = clock.millis();
        CachedPreferences cached = preferencesCache.get();
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.response();
        }
        Map<String, String> values = loadPreferenceValues();
        CommercialDefaultsResponse response = new CommercialDefaultsResponse(
                required(values, SettingKeys.DISPLAY_NAME),
                required(values, SettingKeys.CURRENCY),
                required(values, SettingKeys.LOCALE),
                orDefault(values, SettingKeys.DATE_FORMAT, "medium"),
                precision(orDefault(values, SettingKeys.DECIMAL_PRECISION, "2")),
                orDefault(values, SettingKeys.ROUNDING_RULE, "NONE"),
                orDefault(values, SettingKeys.DEFAULT_PAYMENT_METHOD, "CASH"));
        preferencesCache.set(new CachedPreferences(response, now + PREFERENCES_TTL.toMillis()));
        return response;
    }

    /** Call after admin updates commercial defaults so clients see changes before TTL. */
    public void invalidatePreferencesCache() {
        preferencesCache.set(null);
    }

    private Map<String, String> loadPreferenceValues() {
        List<PlatformSetting> rows = platformSettingRepository.findBySettingKeyIn(PREFERENCE_KEYS);
        Map<String, String> values = new HashMap<>(PREFERENCE_KEYS.size());
        for (PlatformSetting setting : rows) {
            values.put(setting.getSettingKey(), setting.getSettingValue());
        }
        return values;
    }

    private static String orDefault(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            log.warn("Missing platform setting key={}", key);
            throw new ResourceNotFoundException("Required commercial defaults are not configured");
        }
        return value;
    }

    private int precision(String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value < 0 || value > 4 ? 2 : value;
        } catch (NumberFormatException ex) {
            return 2;
        }
    }

    private int appliedMigrations() {
        int cached = cachedAppliedMigrations.get();
        if (cached >= 0) {
            return cached;
        }
        MigrationInfo[] applied = flyway.info().applied();
        int count = applied == null
                ? 0
                : (int) Arrays.stream(applied).filter(migration -> migration != null).count();
        cachedAppliedMigrations.compareAndSet(-1, count);
        return cachedAppliedMigrations.get() >= 0 ? cachedAppliedMigrations.get() : count;
    }

    private record CachedPreferences(CommercialDefaultsResponse response, long expiresAtMillis) {
    }
}
