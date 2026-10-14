package com.aveline.bizflow.purchase;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.common.persistence.AuditableEntity;
import com.aveline.bizflow.finance.FinanceMath;
import com.aveline.bizflow.finance.PaymentMethod;
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
@Table(name = "purchases")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Purchase extends AuditableEntity {

    @Column(name = "purchase_number", nullable = false, length = 32)
    private String purchaseNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "invoice_number", nullable = false, length = 64)
    private String invoiceNumber;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 16)
    private PaymentMethod paymentType;

    @Column(name = "reference_number", length = 64)
    private String referenceNumber;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PurchaseStatus status;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "item_discount", nullable = false, precision = 19, scale = 4)
    private BigDecimal itemDiscount;

    @Column(name = "bill_discount", nullable = false, precision = 19, scale = 4)
    private BigDecimal billDiscount;

    @Column(name = "total_discount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDiscount;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "additional_charges", nullable = false, precision = 19, scale = 4)
    private BigDecimal additionalCharges;

    @Column(name = "round_off", nullable = false, precision = 19, scale = 4)
    private BigDecimal roundOff;

    @Column(name = "grand_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal grandTotal;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal paidAmount;

    @Column(name = "balance_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAmount;

    @Column(name = "returned_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal returnedValue = PurchaseMath.ZERO;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber asc")
    private final List<PurchaseItem> items = new ArrayList<>();

    static Purchase draft(String purchaseNumber) {
        Purchase purchase = new Purchase();
        purchase.purchaseNumber = purchaseNumber;
        purchase.status = PurchaseStatus.DRAFT;
        purchase.returnedValue = PurchaseMath.ZERO;
        return purchase;
    }

    void apply(
            Supplier supplier,
            String invoiceNumber,
            LocalDate purchaseDate,
            PaymentMethod paymentType,
            String referenceNumber,
            String notes,
            PurchaseMath.DocumentQuote quote,
            boolean post) {
        this.supplier = supplier;
        this.invoiceNumber = invoiceNumber;
        this.purchaseDate = purchaseDate;
        this.paymentType = paymentType;
        this.referenceNumber = referenceNumber;
        this.notes = notes;
        this.subtotal = quote.subtotal();
        this.itemDiscount = quote.itemDiscount();
        this.billDiscount = quote.billDiscount();
        this.totalDiscount = quote.totalDiscount();
        this.taxAmount = quote.tax();
        this.additionalCharges = quote.additionalCharges();
        this.roundOff = quote.roundOff();
        this.grandTotal = quote.grandTotal();
        this.paidAmount = quote.paidAmount();
        this.balanceAmount = quote.balanceAmount();
        if (this.returnedValue == null) {
            this.returnedValue = PurchaseMath.ZERO;
        }
        if (post) {
            this.status = PurchaseWorkflow.posted(quote.balanceAmount());
        } else if (this.status != PurchaseStatus.DRAFT) {
            this.status = PurchaseStatus.DRAFT;
        }
    }

    void addItem(PurchaseItem item) {
        items.add(item);
    }

    void clearItems() {
        items.clear();
    }

    void markCancelled() {
        this.status = PurchaseStatus.CANCELLED;
    }

    void markPosted() {
        this.status = PurchaseWorkflow.posted(this.balanceAmount);
    }

    public void settle(BigDecimal amount) {
        BigDecimal open = FinanceMath.openPayable(grandTotal, paidAmount, returnedValue);
        if (amount.compareTo(open) > 0) {
            throw new BusinessRuleException(
                    "ALLOCATION",
                    "allocations",
                    "Allocation exceeds the open balance on " + purchaseNumber);
        }
        this.paidAmount = this.paidAmount.add(amount);
        refreshBalance();
    }

    public void reverseSettlement(BigDecimal amount) {
        if (amount.compareTo(this.paidAmount) > 0) {
            throw new BusinessRuleException(
                    "ALLOCATION",
                    "allocations",
                    "Cannot reverse more than has been paid on " + purchaseNumber);
        }
        this.paidAmount = this.paidAmount.subtract(amount);
        refreshBalance();
    }

    private void refreshBalance() {
        this.balanceAmount = FinanceMath.openPayable(grandTotal, paidAmount, returnedValue);
        if (this.status == PurchaseStatus.CONFIRMED || this.status == PurchaseStatus.COMPLETED) {
            this.status = PurchaseWorkflow.posted(this.balanceAmount);
        }
    }

    void applyReturn(BigDecimal returnedValue, BigDecimal balanceAmount, PurchaseStatus status) {
        this.returnedValue = returnedValue;
        this.balanceAmount = balanceAmount;
        this.status = status;
    }
}
