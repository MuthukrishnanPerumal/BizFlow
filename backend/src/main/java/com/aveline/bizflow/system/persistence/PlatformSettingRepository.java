package com.aveline.bizflow.system.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, Long> {

    Optional<PlatformSetting> findBySettingKey(String settingKey);
}
