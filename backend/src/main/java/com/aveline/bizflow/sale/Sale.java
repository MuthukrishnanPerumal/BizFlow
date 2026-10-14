package com.aveline.bizflow.sale;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.common.persistence.AuditableEntity;
import com.aveline.bizflow.finance.FinanceMath;
import com.aveline.bizflow.master.party.Customer;
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
@Table(name = "sales")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sale extends AuditableEntity {

    @Column(name = "sale_number", nullable = false, length = 32)
    private String saleNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 16)
    private SalePaymentType paymentType;

    @Column(name = "reference_number", length = 64)
    private String referenceNumber;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SaleStatus status;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Column(name = "item_discount", nullable = false, precision = 19, scale = 4)
    private BigDecimal itemDiscount;

    @Column(name = "order_discount", nullable = false, precision = 19, scale = 4)
    private BigDecimal orderDiscount;

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
    private BigDecimal returnedValue = SaleMath.ZERO;

    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundedAmount = SaleMath.ZERO;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber asc")
    private final List<SaleItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber asc")
    private final List<SaleTender> tenders = new ArrayList<>();

    static Sale draft(String saleNumber) {
        Sale sale = new Sale();
        sale.saleNumber = saleNumber;
        sale.status = SaleStatus.DRAFT;
        sale.returnedValue = SaleMath.ZERO;
        sale.refundedAmount = SaleMath.ZERO;
        return sale;
    }

    void apply(
            Customer customer,
            LocalDate invoiceDate,
            SalePaymentType paymentType,
            String referenceNumber,
            String notes,
            SaleMath.DocumentQuote quote,
            boolean post) {
        this.customer = customer;
        this.invoiceDate = invoiceDate;
        this.paymentType = paymentType;
        this.referenceNumber = referenceNumber;
        this.notes = notes;
        this.subtotal = quote.subtotal();
        this.itemDiscount = quote.itemDiscount();
        this.orderDiscount = quote.orderDiscount();
        this.totalDiscount = quote.totalDiscount();
        this.taxAmount = quote.tax();
        this.additionalCharges = quote.additionalCharges();
        this.roundOff = quote.roundOff();
        this.grandTotal = quote.grandTotal();
        this.paidAmount = quote.paidAmount();
        this.balanceAmount = quote.balanceAmount();
        if (this.returnedValue == null) {
            this.returnedValue = SaleMath.ZERO;
        }
        if (this.refundedAmount == null) {
            this.refundedAmount = SaleMath.ZERO;
        }
        if (post) {
            this.status = SaleWorkflow.posted(quote.balanceAmount());
        } else if (this.status != SaleStatus.DRAFT) {
            this.status = SaleStatus.DRAFT;
        }
    }

    void addItem(SaleItem item) {
        items.add(item);
    }

    void addTender(SaleTender tender) {
        tenders.add(tender);
    }

    void clearLines() {
        items.clear();
        tenders.clear();
    }

    void markCancelled() {
        this.status = SaleStatus.CANCELLED;
    }

    void markPosted() {
        this.status = SaleWorkflow.posted(this.balanceAmount);
    }

    public void collect(BigDecimal amount) {
        BigDecimal open = FinanceMath.openReceivable(grandTotal, paidAmount, returnedValue, refundedAmount);
        if (amount.compareTo(open) > 0) {
            throw new BusinessRuleException(
                    "ALLOCATION",
                    "allocations",
                    "Allocation exceeds the open balance on " + saleNumber);
        }
        this.paidAmount = this.paidAmount.add(amount);
        refreshBalance();
    }

    public void reverseCollection(BigDecimal amount) {
        if (amount.compareTo(this.paidAmount) > 0) {
            throw new BusinessRuleException(
                    "ALLOCATION",
                    "allocations",
                    "Cannot reverse more than has been collected on " + saleNumber);
        }
        this.paidAmount = this.paidAmount.subtract(amount);
        refreshBalance();
    }

    private void refreshBalance() {
        this.balanceAmount = FinanceMath.openReceivable(grandTotal, paidAmount, returnedValue, refundedAmount);
        if (this.status == SaleStatus.CONFIRMED || this.status == SaleStatus.COMPLETED) {
            this.status = SaleWorkflow.posted(this.balanceAmount);
        }
    }

    void applyReturn(BigDecimal returnedValue, BigDecimal refundedAmount, BigDecimal balanceAmount, SaleStatus status) {
        this.returnedValue = returnedValue;
        this.refundedAmount = refundedAmount;
        this.balanceAmount = balanceAmount;
        this.status = status;
    }
}
