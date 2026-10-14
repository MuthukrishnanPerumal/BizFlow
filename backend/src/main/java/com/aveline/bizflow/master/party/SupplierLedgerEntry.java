package com.aveline.bizflow.master.party;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import com.aveline.bizflow.master.LedgerEntryType;
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
@Table(name = "supplier_ledger_entry")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplierLedgerEntry extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 32)
    private LedgerEntryType entryType;

    @Column(name = "reference_type", length = 64)
    private String referenceType;

    @Column(name = "reference_id", length = 64)
    private String referenceId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "debit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal debitAmount;

    @Column(name = "credit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditAmount;

    @Column(length = 500)
    private String narration;

    public static SupplierLedgerEntry opening(Supplier supplier, LocalDate entryDate, BigDecimal debit, BigDecimal credit) {
        SupplierLedgerEntry entry = new SupplierLedgerEntry();
        entry.supplier = supplier;
        entry.entryType = LedgerEntryType.OPENING;
        entry.referenceType = "OPENING";
        entry.entryDate = entryDate;
        entry.narration = "Opening balance";
        entry.debitAmount = debit;
        entry.creditAmount = credit;
        return entry;
    }

    public static SupplierLedgerEntry post(
            Supplier supplier,
            LedgerEntryType entryType,
            String referenceType,
            String referenceId,
            LocalDate entryDate,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String narration) {
        SupplierLedgerEntry entry = new SupplierLedgerEntry();
        entry.supplier = supplier;
        entry.entryType = entryType;
        entry.referenceType = referenceType;
        entry.referenceId = referenceId;
        entry.entryDate = entryDate;
        entry.debitAmount = debitAmount;
        entry.creditAmount = creditAmount;
        entry.narration = narration;
        return entry;
    }

    public void replaceOpening(LocalDate entryDate, BigDecimal debit, BigDecimal credit) {
        this.entryDate = entryDate;
        this.debitAmount = debit;
        this.creditAmount = credit;
    }

    public void retire() {
        markDeleted();
    }
}
