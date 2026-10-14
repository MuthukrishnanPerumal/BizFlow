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
@Table(name = "stock_ledger")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockLedgerEntry extends AuditableEntity {

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private StockLocation location;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 32)
    private StockTxnType transactionType;

    @Column(name = "reference_type", nullable = false, length = 64)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, length = 64)
    private String referenceId;

    @Column(name = "reference_number", length = 64)
    private String referenceNumber;

    @Column(name = "batch_code", length = 64)
    private String batchCode;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "quantity_in", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityIn;

    @Column(name = "quantity_out", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityOut;

    @Column(name = "running_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal runningBalance;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "total_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalValue;

    @Column(length = 500)
    private String remarks;

    public static StockLedgerEntry record(
            LocalDate entryDate,
            Product product,
            StockTxnType transactionType,
            String referenceType,
            String referenceId,
            String referenceNumber,
            StockLocation location,
            String batchCode,
            LocalDate expiryDate,
            BigDecimal quantityIn,
            BigDecimal quantityOut,
            BigDecimal runningBalance,
            BigDecimal unitCost,
            BigDecimal totalValue,
            String remarks) {
        StockLedgerEntry entry = new StockLedgerEntry();
        entry.entryDate = entryDate;
        entry.product = product;
        entry.location = location;
        entry.transactionType = transactionType;
        entry.referenceType = referenceType;
        entry.referenceId = referenceId;
        entry.referenceNumber = referenceNumber;
        entry.batchCode = batchCode;
        entry.expiryDate = expiryDate;
        entry.quantityIn = quantityIn;
        entry.quantityOut = quantityOut;
        entry.runningBalance = runningBalance;
        entry.unitCost = unitCost;
        entry.totalValue = totalValue;
        entry.remarks = remarks;
        return entry;
    }
}
