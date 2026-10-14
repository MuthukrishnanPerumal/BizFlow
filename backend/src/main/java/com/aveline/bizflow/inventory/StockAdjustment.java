package com.aveline.bizflow.inventory;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import com.aveline.bizflow.master.catalog.Product;
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

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "stock_adjustment")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockAdjustment extends AuditableEntity {

    @Column(name = "adjustment_number", nullable = false, length = 32)
    private String adjustmentNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private StockLocation location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "batch_code", length = 64)
    private String batchCode;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AdjustmentDirection direction;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "total_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AdjustmentReason reason;

    @Column(name = "reference_number", length = 64)
    private String referenceNumber;

    @Column(name = "adjustment_date", nullable = false)
    private LocalDate adjustmentDate;

    @Column(length = 1000)
    private String notes;

    public static StockAdjustment post(
            String adjustmentNumber,
            StockLocation location,
            Product product,
            String batchCode,
            LocalDate expiryDate,
            AdjustmentDirection direction,
            BigDecimal quantity,
            BigDecimal unitCost,
            BigDecimal totalValue,
            AdjustmentReason reason,
            String referenceNumber,
            LocalDate adjustmentDate,
            String notes) {
        StockAdjustment adjustment = new StockAdjustment();
        adjustment.adjustmentNumber = adjustmentNumber;
        adjustment.location = location;
        adjustment.product = product;
        adjustment.batchCode = batchCode == null || batchCode.isBlank() ? null : batchCode;
        adjustment.expiryDate = expiryDate;
        adjustment.direction = direction;
        adjustment.quantity = quantity;
        adjustment.unitCost = unitCost;
        adjustment.totalValue = totalValue;
        adjustment.reason = reason;
        adjustment.referenceNumber = referenceNumber;
        adjustment.adjustmentDate = adjustmentDate;
        adjustment.notes = notes;
        return adjustment;
    }
}
