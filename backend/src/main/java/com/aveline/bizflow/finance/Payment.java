package com.aveline.bizflow.finance;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import com.aveline.bizflow.master.party.Customer;
import com.aveline.bizflow.master.party.Supplier;
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
@Table(name = "payments")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends AuditableEntity {

    @Column(name = "payment_number", nullable = false, length = 32)
    private String paymentNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PaymentDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 16)
    private PaymentMethod paymentMethod;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedAmount;

    @Column(name = "unallocated_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal unallocatedAmount;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;

    @Column(name = "reference_number", length = 64)
    private String referenceNumber;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    public static Payment supplierPayment(
            String paymentNumber,
            Supplier supplier,
            PaymentMethod paymentMethod,
            LocalDate paymentDate,
            BigDecimal amount,
            String sourceType,
            String sourceId,
            String referenceNumber,
            String notes) {
        Payment payment = new Payment();
        payment.paymentNumber = paymentNumber;
        payment.supplier = supplier;
        payment.direction = PaymentDirection.OUT;
        payment.paymentMethod = paymentMethod;
        payment.paymentDate = paymentDate;
        payment.amount = amount;
        payment.sourceType = sourceType;
        payment.sourceId = sourceId;
        payment.referenceNumber = referenceNumber;
        payment.notes = notes;
        payment.status = PaymentStatus.POSTED;
        payment.allocatedAmount = amount;
        payment.unallocatedAmount = FinanceMath.ZERO;
        return payment;
    }

    public static Payment customerReceipt(
            String paymentNumber,
            Customer customer,
            PaymentMethod paymentMethod,
            LocalDate paymentDate,
            BigDecimal amount,
            String sourceType,
            String sourceId,
            String referenceNumber,
            String notes) {
        return party(paymentNumber, null, customer, PaymentDirection.IN, paymentMethod, paymentDate, amount, sourceType, sourceId, referenceNumber, notes);
    }

    public static Payment customerRefund(
            String paymentNumber,
            Customer customer,
            PaymentMethod paymentMethod,
            LocalDate paymentDate,
            BigDecimal amount,
            String sourceType,
            String sourceId,
            String referenceNumber,
            String notes) {
        return party(paymentNumber, null, customer, PaymentDirection.OUT, paymentMethod, paymentDate, amount, sourceType, sourceId, referenceNumber, notes);
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELLED;
    }

    private static Payment party(
            String paymentNumber,
            Supplier supplier,
            Customer customer,
            PaymentDirection direction,
            PaymentMethod paymentMethod,
            LocalDate paymentDate,
            BigDecimal amount,
            String sourceType,
            String sourceId,
            String referenceNumber,
            String notes) {
        Payment payment = new Payment();
        payment.paymentNumber = paymentNumber;
        payment.supplier = supplier;
        payment.customer = customer;
        payment.direction = direction;
        payment.paymentMethod = paymentMethod;
        payment.paymentDate = paymentDate;
        payment.amount = amount;
        payment.sourceType = sourceType;
        payment.sourceId = sourceId;
        payment.referenceNumber = referenceNumber;
        payment.notes = notes;
        payment.status = PaymentStatus.POSTED;
        payment.allocatedAmount = amount;
        payment.unallocatedAmount = FinanceMath.ZERO;
        return payment;
    }

    public static Payment settlement(
            String paymentNumber,
            Supplier supplier,
            Customer customer,
            PaymentDirection direction,
            PaymentMethod paymentMethod,
            LocalDate paymentDate,
            BigDecimal amount,
            BigDecimal allocatedAmount,
            String sourceType,
            String referenceNumber,
            String notes) {
        Payment payment = party(
                paymentNumber,
                supplier,
                customer,
                direction,
                paymentMethod,
                paymentDate,
                amount,
                sourceType,
                paymentNumber,
                referenceNumber,
                notes);
        payment.assignSettlement(allocatedAmount, amount.subtract(allocatedAmount));
        return payment;
    }

    public void assignSettlement(BigDecimal allocatedAmount, BigDecimal unallocatedAmount) {
        this.allocatedAmount = allocatedAmount;
        this.unallocatedAmount = unallocatedAmount;
    }

    public void revise(
            PaymentMethod paymentMethod,
            LocalDate paymentDate,
            BigDecimal amount,
            BigDecimal allocatedAmount,
            String referenceNumber,
            String notes) {
        this.paymentMethod = paymentMethod;
        this.paymentDate = paymentDate;
        this.amount = amount;
        this.referenceNumber = referenceNumber;
        this.notes = notes;
        assignSettlement(allocatedAmount, amount.subtract(allocatedAmount));
    }

    public boolean standalone() {
        return FinancePolicy.CUSTOMER_PAYMENT.equals(sourceType) || FinancePolicy.SUPPLIER_PAYMENT.equals(sourceType);
    }
}
