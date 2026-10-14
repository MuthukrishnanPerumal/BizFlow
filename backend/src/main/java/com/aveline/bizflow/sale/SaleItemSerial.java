package com.aveline.bizflow.sale;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "sale_item_serial")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleItemSerial extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_item_id", nullable = false)
    private SaleItem item;

    @Column(name = "serial_number", nullable = false, length = 64)
    private String serialNumber;

    @Column(nullable = false, columnDefinition = "bit(1)")
    private boolean returned;

    static SaleItemSerial issue(SaleItem item, String serialNumber) {
        SaleItemSerial serial = new SaleItemSerial();
        serial.item = item;
        serial.serialNumber = serialNumber;
        serial.returned = false;
        return serial;
    }

    void markReturned() {
        this.returned = true;
    }
}
