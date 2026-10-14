package com.aveline.bizflow.sale;

import com.aveline.bizflow.common.persistence.AuditableEntity;
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
@Table(name = "sales_returns")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleReturn extends AuditableEntity {

    @Column(name = "return_number", nullable = false, length = 32)
    private String returnNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_method", nullable = false, length = 16)
    private RefundMethod refundMethod;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SaleReturnStatus status;

    @Column(name = "merchandise_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal merchandiseTotal;

    @Column(name = "item_discount_reversal", nullable = false, precision = 19, scale = 4)
    private BigDecimal itemDiscountReversal;

    @Column(name = "order_discount_reversal", nullable = false, precision = 19, scale = 4)
    private BigDecimal orderDiscountReversal;

    @Column(name = "tax_reversal", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxReversal;

    @Column(name = "grand_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal grandTotal;

    @OneToMany(mappedBy = "saleReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber asc")
    private final List<SaleReturnItem> items = new ArrayList<>();

    static SaleReturn open(
            String returnNumber,
            Sale sale,
            LocalDate returnDate,
            String reason,
            RefundMethod refundMethod,
            String notes) {
        SaleReturn saleReturn = new SaleReturn();
        saleReturn.returnNumber = returnNumber;
        saleReturn.sale = sale;
        saleReturn.customer = sale.getCustomer();
        saleReturn.returnDate = returnDate;
        saleReturn.reason = reason;
        saleReturn.refundMethod = refundMethod;
        saleReturn.notes = notes;
        saleReturn.status = SaleReturnStatus.CONFIRMED;
        saleReturn.merchandiseTotal = SaleMath.ZERO;
        saleReturn.itemDiscountReversal = SaleMath.ZERO;
        saleReturn.orderDiscountReversal = SaleMath.ZERO;
        saleReturn.taxReversal = SaleMath.ZERO;
        saleReturn.grandTotal = SaleMath.ZERO;
        return saleReturn;
    }

    void addItem(SaleReturnItem item) {
        items.add(item);
    }

    void totals(
            BigDecimal merchandiseTotal,
            BigDecimal itemDiscountReversal,
            BigDecimal orderDiscountReversal,
            BigDecimal taxReversal,
            BigDecimal grandTotal) {
        this.merchandiseTotal = merchandiseTotal;
        this.itemDiscountReversal = itemDiscountReversal;
        this.orderDiscountReversal = orderDiscountReversal;
        this.taxReversal = taxReversal;
        this.grandTotal = grandTotal;
    }
}
