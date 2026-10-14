package com.aveline.bizflow.finance;

import com.aveline.bizflow.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "expenses")
@SQLRestriction("is_deleted = 0")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense extends AuditableEntity {

    @Column(name = "expense_number", nullable = false, length = 32)
    private String expenseNumber;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExpenseCategory category;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 16)
    private PaymentMethod paymentMethod;

    @Column(name = "reference_number", length = 64)
    private String referenceNumber;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExpenseStatus status;

    public static Expense post(
            String expenseNumber,
            LocalDate expenseDate,
            ExpenseCategory category,
            String description,
            BigDecimal amount,
            PaymentMethod paymentMethod,
            String referenceNumber,
            String notes) {
        Expense expense = new Expense();
        expense.expenseNumber = expenseNumber;
        expense.status = ExpenseStatus.POSTED;
        expense.apply(expenseDate, category, description, amount, paymentMethod, referenceNumber, notes);
        return expense;
    }

    public void revise(
            LocalDate expenseDate,
            ExpenseCategory category,
            String description,
            BigDecimal amount,
            PaymentMethod paymentMethod,
            String referenceNumber,
            String notes) {
        apply(expenseDate, category, description, amount, paymentMethod, referenceNumber, notes);
    }

    public void cancel() {
        this.status = ExpenseStatus.CANCELLED;
    }

    private void apply(
            LocalDate expenseDate,
            ExpenseCategory category,
            String description,
            BigDecimal amount,
            PaymentMethod paymentMethod,
            String referenceNumber,
            String notes) {
        this.expenseDate = expenseDate;
        this.category = category;
        this.description = description;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.referenceNumber = referenceNumber;
        this.notes = notes;
    }
}
