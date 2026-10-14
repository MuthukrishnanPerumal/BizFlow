package com.aveline.bizflow.inventory;

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
@Table(name = "stock_transfer_serial")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockTransferSerial extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_line_id", nullable = false)
    private StockTransferLine line;

    @Column(name = "serial_number", nullable = false, length = 64)
    private String serialNumber;

    static StockTransferSerial capture(StockTransferLine line, String serialNumber) {
        StockTransferSerial serial = new StockTransferSerial();
        serial.line = line;
        serial.serialNumber = serialNumber;
        return serial;
    }
}
