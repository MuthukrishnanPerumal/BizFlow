package com.aveline.bizflow.purchase;

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
@Table(name = "purchase_item_serial")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseItemSerial extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_item_id", nullable = false)
    private PurchaseItem item;

    @Column(name = "serial_number", nullable = false, length = 64)
    private String serialNumber;

    @Column(nullable = false, columnDefinition = "bit(1)")
    private boolean returned;

    static PurchaseItemSerial receive(PurchaseItem item, String serialNumber) {
        PurchaseItemSerial serial = new PurchaseItemSerial();
        serial.item = item;
        serial.serialNumber = serialNumber;
        serial.returned = false;
        return serial;
    }

    void markReturned() {
        this.returned = true;
    }
}
