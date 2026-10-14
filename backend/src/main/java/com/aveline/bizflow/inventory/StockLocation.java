package com.aveline.bizflow.inventory;

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
@Table(name = "stock_location")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockLocation extends AuditableEntity {

    @Column(name = "location_code", nullable = false, length = 32)
    private String locationCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "is_default", nullable = false, columnDefinition = "bit(1)")
    private boolean defaultLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status;

    public static StockLocation create(String locationCode, String name, boolean defaultLocation) {
        StockLocation location = new StockLocation();
        location.locationCode = locationCode;
        location.name = name;
        location.defaultLocation = defaultLocation;
        location.status = RecordStatus.ACTIVE;
        return location;
    }
}
