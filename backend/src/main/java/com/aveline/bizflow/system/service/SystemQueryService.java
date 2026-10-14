package com.aveline.bizflow.system.service;

import com.aveline.bizflow.common.exception.ResourceNotFoundException;
import com.aveline.bizflow.system.SettingKeys;
import com.aveline.bizflow.system.dto.CommercialDefaultsResponse;
import com.aveline.bizflow.system.dto.SystemHealthResponse;
import com.aveline.bizflow.system.dto.SystemInfoResponse;
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
import java.util.Arrays;

@Slf4j
@Service
public class SystemQueryService {

    static final String DISPLAY_NAME_KEY = SettingKeys.DISPLAY_NAME;
    static final String CURRENCY_KEY = SettingKeys.CURRENCY;
    static final String LOCALE_KEY = SettingKeys.LOCALE;

    private final DataSource dataSource;
    private final Flyway flyway;
    private final PlatformSettingRepository platformSettingRepository;
    private final Environment environment;
    private final BuildProperties buildProperties;
    private final Clock clock;

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
        return new CommercialDefaultsResponse(
                requiredSetting(DISPLAY_NAME_KEY),
                requiredSetting(CURRENCY_KEY),
                requiredSetting(LOCALE_KEY),
                settingOrDefault(SettingKeys.DATE_FORMAT, "medium"),
                precision(settingOrDefault(SettingKeys.DECIMAL_PRECISION, "2")),
                settingOrDefault(SettingKeys.ROUNDING_RULE, "NONE"),
                settingOrDefault(SettingKeys.DEFAULT_PAYMENT_METHOD, "CASH"));
    }

    private String settingOrDefault(String key, String fallback) {
        return platformSettingRepository.findBySettingKey(key)
                .map(setting -> setting.getSettingValue())
                .filter(value -> value != null && !value.isBlank())
                .orElse(fallback);
    }

    private int precision(String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value < 0 || value > 4 ? 2 : value;
        } catch (NumberFormatException ex) {
            return 2;
        }
    }

    private String requiredSetting(String key) {
        return platformSettingRepository.findBySettingKey(key)
                .map(setting -> setting.getSettingValue())
                .filter(value -> value != null && !value.isBlank())
                .orElseThrow(() -> {
                    log.warn("Missing platform setting key={}", key);
                    return new ResourceNotFoundException("Required commercial defaults are not configured");
                });
    }

    private int appliedMigrations() {
        MigrationInfo[] applied = flyway.info().applied();
        if (applied == null) {
            return 0;
        }
        return (int) Arrays.stream(applied).filter(migration -> migration != null).count();
    }
}
