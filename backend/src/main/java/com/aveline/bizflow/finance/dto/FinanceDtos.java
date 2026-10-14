package com.aveline.bizflow.finance.dto;

import com.aveline.bizflow.finance.AllocationStatus;
import com.aveline.bizflow.finance.ExpenseCategory;
import com.aveline.bizflow.finance.ExpenseStatus;
import com.aveline.bizflow.finance.JournalStatus;
import com.aveline.bizflow.finance.PaymentMethod;
import com.aveline.bizflow.finance.PaymentStatus;
import com.aveline.bizflow.finance.PositionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class FinanceDtos {

    private FinanceDtos() {
    }

    public record AllocationRequest(
            @NotNull Long invoiceId,
            @NotNull BigDecimal amount
    ) {
    }

    public record CustomerPaymentRequest(
            @NotNull Long customerId,
            @NotNull LocalDate paymentDate,
            @NotNull BigDecimal amount,
            @NotNull PaymentMethod paymentMethod,
            @Size(max = 64) String referenceNumber,
            @Size(max = 500) String notes,
            List<@Valid AllocationRequest> allocations
    ) {
    }

    public record SupplierPaymentRequest(
            @NotNull Long supplierId,
            @NotNull LocalDate paymentDate,
            @NotNull BigDecimal amount,
            @NotNull PaymentMethod paymentMethod,
            @Size(max = 64) String referenceNumber,
            @Size(max = 500) String notes,
            List<@Valid AllocationRequest> allocations
    ) {
    }

    public record CustomerPaymentUpdateRequest(
            @NotNull Long customerId,
            @NotNull LocalDate paymentDate,
            @NotNull BigDecimal amount,
            @NotNull PaymentMethod paymentMethod,
            @Size(max = 64) String referenceNumber,
            @Size(max = 500) String notes,
            List<@Valid AllocationRequest> allocations,
            @NotNull Long version
    ) {
    }

    public record SupplierPaymentUpdateRequest(
            @NotNull Long supplierId,
            @NotNull LocalDate paymentDate,
            @NotNull BigDecimal amount,
            @NotNull PaymentMethod paymentMethod,
            @Size(max = 64) String referenceNumber,
            @Size(max = 500) String notes,
            List<@Valid AllocationRequest> allocations,
            @NotNull Long version
    ) {
    }

    public record VersionRequest(@NotNull Long version) {
    }

    public record ExpenseRequest(
            @NotNull LocalDate expenseDate,
            @NotNull ExpenseCategory category,
            @NotBlank @Size(max = 500) String description,
            @NotNull BigDecimal amount,
            @NotNull PaymentMethod paymentMethod,
            @Size(max = 64) String referenceNumber,
            @Size(max = 1000) String notes
    ) {
    }

    public record ExpenseUpdateRequest(
            @NotNull LocalDate expenseDate,
            @NotNull ExpenseCategory category,
            @NotBlank @Size(max = 500) String description,
            @NotNull BigDecimal amount,
            @NotNull PaymentMethod paymentMethod,
            @Size(max = 64) String referenceNumber,
            @Size(max = 1000) String notes,
            @NotNull Long version
    ) {
    }

    public record AllocationView(
            Long invoiceId,
            String documentType,
            String documentNumber,
            BigDecimal amount,
            AllocationStatus status
    ) {
    }

    public record JournalLineView(
            int lineNumber,
            String accountCode,
            String accountName,
            BigDecimal debit,
            BigDecimal credit
    ) {
    }

    public record AuditView(
            String eventType,
            String actor,
            String action,
            String outcome,
            String detail,
            Instant createdAt
    ) {
    }

    public record PaymentSummary(
            Long id,
            String paymentNumber,
            LocalDate paymentDate,
            Long partyId,
            String partyCode,
            String partyName,
            PaymentMethod paymentMethod,
            BigDecimal amount,
            BigDecimal allocatedAmount,
            BigDecimal unallocatedAmount,
            PaymentStatus status,
            String referenceNumber,
            String sourceType,
            boolean editable
    ) {
    }

    public record PaymentDetail(
            Long id,
            String paymentNumber,
            LocalDate paymentDate,
            Long partyId,
            String partyCode,
            String partyName,
            PaymentMethod paymentMethod,
            BigDecimal amount,
            BigDecimal allocatedAmount,
            BigDecimal unallocatedAmount,
            PaymentStatus status,
            String referenceNumber,
            String notes,
            String sourceType,
            String sourceId,
            boolean editable,
            BigDecimal partyOutstanding,
            JournalStatus journalStatus,
            List<AllocationView> allocations,
            List<JournalLineView> journal,
            List<AuditView> audits,
            Long version
    ) {
    }

    public record PaymentReport(
            List<PaymentSummary> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            BigDecimal postedTotal,
            BigDecimal advanceTotal,
            long postedCount
    ) {
    }

    public record OpenInvoice(
            Long invoiceId,
            String documentNumber,
            LocalDate documentDate,
            BigDecimal grandTotal,
            BigDecimal paidAmount,
            BigDecimal openBalance,
            LocalDate dueDate,
            boolean overdue,
            boolean dueDateSupported
    ) {
    }

    public record ExpenseSummary(
            Long id,
            String expenseNumber,
            LocalDate expenseDate,
            ExpenseCategory category,
            String description,
            BigDecimal amount,
            PaymentMethod paymentMethod,
            ExpenseStatus status,
            String referenceNumber
    ) {
    }

    public record ExpenseDetail(
            Long id,
            String expenseNumber,
            LocalDate expenseDate,
            ExpenseCategory category,
            String description,
            BigDecimal amount,
            PaymentMethod paymentMethod,
            String referenceNumber,
            String notes,
            ExpenseStatus status,
            JournalStatus journalStatus,
            List<JournalLineView> journal,
            List<AuditView> audits,
            Long version
    ) {
    }

    public record ExpenseReport(
            List<ExpenseSummary> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            BigDecimal postedTotal
    ) {
    }

    public record ReceivableRow(
            Long customerId,
            String customerCode,
            String customerName,
            String paymentTerms,
            BigDecimal totalSales,
            BigDecimal totalReturns,
            BigDecimal totalPayments,
            BigDecimal outstanding,
            Boolean overdueSupported,
            BigDecimal overdueAmount,
            PositionStatus status
    ) {
    }

    public record PayableRow(
            Long supplierId,
            String supplierCode,
            String supplierName,
            String paymentTerms,
            BigDecimal totalPurchases,
            BigDecimal totalReturns,
            BigDecimal totalPayments,
            BigDecimal outstanding,
            Boolean overdueSupported,
            BigDecimal overdueAmount,
            PositionStatus status
    ) {
    }

    public record ReceivableReport(
            List<ReceivableRow> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            BigDecimal totalOutstanding,
            BigDecimal totalOverdue,
            BigDecimal totalSales,
            BigDecimal totalPayments,
            long overdueCustomers
    ) {
    }

    public record PayableReport(
            List<PayableRow> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            BigDecimal totalOutstanding,
            BigDecimal totalOverdue,
            BigDecimal totalPurchases,
            BigDecimal totalPayments,
            long overdueSuppliers
    ) {
    }
}
