package com.aveline.bizflow.purchase;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import com.aveline.bizflow.master.party.Supplier;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "purchase_returns")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseReturn extends AuditableEntity {

    @Column(name = "return_number", nullable = false, length = 32)
    private String returnNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PurchaseReturnStatus status;

    @Column(name = "merchandise_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal merchandiseTotal;

    @Column(name = "grand_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal grandTotal;

    @OneToMany(mappedBy = "purchaseReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber asc")
    private final List<PurchaseReturnItem> items = new ArrayList<>();

    static PurchaseReturn open(String returnNumber, Purchase purchase, LocalDate returnDate, String notes) {
        PurchaseReturn purchaseReturn = new PurchaseReturn();
        purchaseReturn.returnNumber = returnNumber;
        purchaseReturn.purchase = purchase;
        purchaseReturn.supplier = purchase.getSupplier();
        purchaseReturn.returnDate = returnDate;
        purchaseReturn.notes = notes;
        purchaseReturn.status = PurchaseReturnStatus.CONFIRMED;
        purchaseReturn.merchandiseTotal = PurchaseMath.ZERO;
        purchaseReturn.grandTotal = PurchaseMath.ZERO;
        return purchaseReturn;
    }

    void addItem(PurchaseReturnItem item) {
        items.add(item);
    }

    void totals(BigDecimal merchandiseTotal, BigDecimal grandTotal) {
        this.merchandiseTotal = merchandiseTotal;
        this.grandTotal = grandTotal;
    }
}
