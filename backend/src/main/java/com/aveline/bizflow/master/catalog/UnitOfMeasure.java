package com.aveline.bizflow.master.catalog;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import com.aveline.bizflow.master.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@Table(name = "units")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UnitOfMeasure extends AuditableEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "short_name", nullable = false, length = 16)
    private String shortName;

    @Column(length = 500)
    private String description;

    @Column(name = "decimal_allowed", nullable = false, columnDefinition = "bit(1)")
    private boolean decimalAllowed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status;

    public static UnitOfMeasure create(
            String name,
            String shortName,
            String description,
            boolean decimalAllowed,
            RecordStatus status) {
        UnitOfMeasure unit = new UnitOfMeasure();
        unit.name = name;
        unit.shortName = shortName;
        unit.description = description;
        unit.decimalAllowed = decimalAllowed;
        unit.status = status;
        return unit;
    }

    public void update(String name, String shortName, String description, boolean decimalAllowed, RecordStatus status) {
        this.name = name;
        this.shortName = shortName;
        this.description = description;
        this.decimalAllowed = decimalAllowed;
        this.status = status;
    }

    public void retire() {
        markDeleted();
    }
}
