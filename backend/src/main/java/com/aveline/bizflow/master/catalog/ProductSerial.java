package com.aveline.bizflow.master.catalog;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import com.aveline.bizflow.master.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Entity
@Table(name = "product_serial")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductSerial extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "serial_number", nullable = false, length = 64)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RecordStatus status;

    public static ProductSerial receive(Product product, String serialNumber) {
        ProductSerial serial = new ProductSerial();
        serial.product = product;
        serial.serialNumber = serialNumber;
        serial.status = RecordStatus.ACTIVE;
        return serial;
    }

    public void place(Long locationId) {
        this.locationId = locationId;
    }

    public void activate() {
        this.status = RecordStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = RecordStatus.INACTIVE;
    }
}
