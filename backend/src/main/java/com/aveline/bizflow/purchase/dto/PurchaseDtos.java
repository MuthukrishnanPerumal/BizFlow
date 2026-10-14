package com.aveline.bizflow.purchase.dto;

import com.aveline.bizflow.finance.PaymentMethod;
import com.aveline.bizflow.finance.PaymentStatus;
import com.aveline.bizflow.inventory.StockTxnType;
import com.aveline.bizflow.purchase.PurchaseReturnStatus;
import com.aveline.bizflow.purchase.PurchaseStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PurchaseDtos {

    private PurchaseDtos() {
    }

    public record PurchaseLineRequest(
            @NotNull Long productId,
            @Size(max = 64) String barcode,
            @Size(max = 64) String batch,
            LocalDate expiryDate,
            @NotNull BigDecimal quantity,
            BigDecimal freeQuantity,
            @NotNull BigDecimal purchasePrice,
            BigDecimal discount,
            @NotNull BigDecimal tax,
            @Size(max = 500) List<@NotBlank @Size(max = 64) String> serials
    ) {
    }

    public record PurchaseRequest(
            @NotNull Long supplierId,
            @NotBlank @Size(max = 64) String invoiceNumber,
            @NotNull LocalDate purchaseDate,
            @NotNull PaymentMethod paymentType,
            @Size(max = 64) String referenceNumber,
            @Size(max = 1000) String notes,
            BigDecimal billDiscount,
            BigDecimal additionalCharges,
            BigDecimal roundOff,
            BigDecimal paidAmount,
            @NotNull Boolean confirm,
            @NotEmpty @Valid List<PurchaseLineRequest> items
    ) {
    }

    public record PurchaseUpdateRequest(
            @NotNull Long supplierId,
            @NotBlank @Size(max = 64) String invoiceNumber,
            @NotNull LocalDate purchaseDate,
            @NotNull PaymentMethod paymentType,
            @Size(max = 64) String referenceNumber,
            @Size(max = 1000) String notes,
            BigDecimal billDiscount,
            BigDecimal additionalCharges,
            BigDecimal roundOff,
            BigDecimal paidAmount,
            @NotNull Boolean confirm,
            @NotNull Long version,
            @NotEmpty @Valid List<PurchaseLineRequest> items
    ) {
    }

    public record VersionRequest(@NotNull Long version) {
    }

    public record ReturnLineRequest(
            @NotNull Long purchaseItemId,
            @NotNull BigDecimal quantity,
            BigDecimal freeQuantity,
            @Size(max = 500) List<@NotBlank @Size(max = 64) String> serials
    ) {
    }

    public record PurchaseReturnRequest(
            @NotNull Long purchaseId,
            @NotNull Long purchaseVersion,
            @NotNull LocalDate returnDate,
            @Size(max = 1000) String notes,
            @NotEmpty @Valid List<ReturnLineRequest> items
    ) {
    }

    public record PurchaseSummary(
            Long id,
            String purchaseNumber,
            String invoiceNumber,
            LocalDate purchaseDate,
            Long supplierId,
            String supplierCode,
            String supplierName,
            PaymentMethod paymentType,
            PurchaseStatus status,
            BigDecimal grandTotal,
            BigDecimal paidAmount,
            BigDecimal balanceAmount,
            Long version
    ) {
    }

    public record SupplierInfo(
            Long id,
            String supplierCode,
            String supplierName,
            String companyName,
            String phone,
            String email,
            String taxNumber,
            String address,
            String city,
            String state,
            BigDecimal balance
    ) {
    }

    public record PurchaseLineResponse(
            Long id,
            int lineNumber,
            Long productId,
            String productCode,
            String productName,
            String unitName,
            boolean batchTracking,
            boolean serialTracking,
            boolean expiryTracking,
            boolean decimalAllowed,
            String barcode,
            String batch,
            LocalDate expiryDate,
            BigDecimal quantity,
            BigDecimal freeQuantity,
            BigDecimal purchasePrice,
            BigDecimal discount,
            BigDecimal tax,
            BigDecimal taxAmount,
            BigDecimal total,
            BigDecimal returnedQuantity,
            BigDecimal returnedFreeQuantity,
            BigDecimal remainingQuantity,
            BigDecimal remainingFreeQuantity,
            List<String> serials
    ) {
    }

    public record PaymentInfo(
            Long id,
            String paymentNumber,
            LocalDate paymentDate,
            PaymentMethod paymentMethod,
            BigDecimal amount,
            PaymentStatus status,
            String referenceNumber
    ) {
    }

    public record StockImpact(
            Long id,
            LocalDate date,
            Long productId,
            String productName,
            StockTxnType transactionType,
            String referenceType,
            String referenceId,
            String batch,
            LocalDate expiryDate,
            BigDecimal quantityIn,
            BigDecimal quantityOut,
            BigDecimal runningBalance,
            BigDecimal unitCost,
            BigDecimal totalValue,
            String user,
            String remarks
    ) {
    }

    public record AuditInfo(
            Long id,
            String eventType,
            String action,
            String actor,
            String outcome,
            String detail,
            Instant createdAt
    ) {
    }

    public record ReturnLineResponse(
            Long id,
            Long purchaseItemId,
            String productName,
            BigDecimal quantity,
            BigDecimal freeQuantity,
            BigDecimal purchasePrice,
            BigDecimal discount,
            BigDecimal taxAmount,
            BigDecimal total,
            List<String> serials
    ) {
    }

    public record ReturnInfo(
            Long id,
            String returnNumber,
            LocalDate returnDate,
            PurchaseReturnStatus status,
            BigDecimal merchandiseTotal,
            BigDecimal grandTotal,
            String notes,
            List<ReturnLineResponse> items
    ) {
    }

    public record PurchaseDetail(
            Long id,
            String purchaseNumber,
            SupplierInfo supplier,
            String invoiceNumber,
            LocalDate purchaseDate,
            PaymentMethod paymentType,
            String referenceNumber,
            String notes,
            PurchaseStatus status,
            BigDecimal subtotal,
            BigDecimal itemDiscount,
            BigDecimal billDiscount,
            BigDecimal totalDiscount,
            BigDecimal tax,
            BigDecimal additionalCharges,
            BigDecimal roundOff,
            BigDecimal grandTotal,
            BigDecimal paidAmount,
            BigDecimal balanceAmount,
            BigDecimal returnedValue,
            boolean editable,
            boolean cancellable,
            boolean returnable,
            List<PurchaseLineResponse> items,
            List<PaymentInfo> payments,
            List<StockImpact> stockImpact,
            List<ReturnInfo> returns,
            List<AuditInfo> audits,
            Instant createdAt,
            String createdBy,
            Long version
    ) {
    }

    public record ReturnSummary(
            Long id,
            String returnNumber,
            LocalDate returnDate,
            Long purchaseId,
            String purchaseNumber,
            String invoiceNumber,
            Long supplierId,
            String supplierName,
            BigDecimal grandTotal,
            PurchaseReturnStatus status
    ) {
    }

    public record ReturnDetail(
            Long id,
            String returnNumber,
            Long purchaseId,
            String purchaseNumber,
            String invoiceNumber,
            SupplierInfo supplier,
            LocalDate returnDate,
            String notes,
            PurchaseReturnStatus status,
            BigDecimal merchandiseTotal,
            BigDecimal grandTotal,
            List<ReturnLineResponse> items,
            List<StockImpact> stockImpact,
            Instant createdAt,
            String createdBy,
            Long version
    ) {
    }
}
