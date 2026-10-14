package com.aveline.bizflow.system.persistence;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@SQLRestriction("is_deleted = 0")
@Table(
        name = "platform_setting",
        uniqueConstraints = @UniqueConstraint(name = "uk_platform_setting_key", columnNames = "setting_key")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformSetting extends AuditableEntity {

    @Column(name = "setting_key", nullable = false, length = 128)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 2000)
    private String settingValue;

    @Column(length = 500)
    private String description;

    public void revise(String value) {
        this.settingValue = value == null ? "" : value;
    }
}
