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
@Table(name = "sales_return_serial")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleReturnSerial extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_return_item_id", nullable = false)
    private SaleReturnItem item;

    @Column(name = "serial_number", nullable = false, length = 64)
    private String serialNumber;

    static SaleReturnSerial returned(SaleReturnItem item, String serialNumber) {
        SaleReturnSerial serial = new SaleReturnSerial();
        serial.item = item;
        serial.serialNumber = serialNumber;
        return serial;
    }
}
