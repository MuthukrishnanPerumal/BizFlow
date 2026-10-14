package com.aveline.bizflow.sale.dto;

import com.aveline.bizflow.finance.PaymentMethod;
import com.aveline.bizflow.finance.PaymentStatus;
import com.aveline.bizflow.inventory.StockTxnType;
import com.aveline.bizflow.sale.RefundMethod;
import com.aveline.bizflow.sale.SalePaymentType;
import com.aveline.bizflow.sale.SaleReturnStatus;
import com.aveline.bizflow.sale.SaleStatus;
import com.aveline.bizflow.sale.SaleTenderMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SaleDtos {

    private SaleDtos() {
    }

    public record SaleLineRequest(
            @NotNull Long productId,
            @Size(max = 64) String barcode,
            @Size(max = 64) String batch,
            LocalDate expiryDate,
            @NotNull BigDecimal quantity,
            @NotNull BigDecimal sellingPrice,
            BigDecimal discount,
            @NotNull BigDecimal tax,
            @Size(max = 500) List<@NotBlank @Size(max = 64) String> serials
    ) {
    }

    public record TenderRequest(
            @NotNull SaleTenderMethod method,
            @NotNull BigDecimal amount,
            @Size(max = 64) String referenceNumber
    ) {
    }

    public record SaleRequest(
            @NotNull Long customerId,
            @NotNull LocalDate invoiceDate,
            @NotNull SalePaymentType paymentType,
            @Size(max = 64) String referenceNumber,
            @Size(max = 1000) String notes,
            BigDecimal orderDiscount,
            BigDecimal additionalCharges,
            BigDecimal roundOff,
            BigDecimal paidAmount,
            @NotNull Boolean confirm,
            @NotEmpty @Valid List<SaleLineRequest> items,
            @Valid List<TenderRequest> tenders
    ) {
    }

    public record SaleUpdateRequest(
            @NotNull Long customerId,
            @NotNull LocalDate invoiceDate,
            @NotNull SalePaymentType paymentType,
            @Size(max = 64) String referenceNumber,
            @Size(max = 1000) String notes,
            BigDecimal orderDiscount,
            BigDecimal additionalCharges,
            BigDecimal roundOff,
            BigDecimal paidAmount,
            @NotNull Boolean confirm,
            @NotNull Long version,
            @NotEmpty @Valid List<SaleLineRequest> items,
            @Valid List<TenderRequest> tenders
    ) {
    }

    public record VersionRequest(@NotNull Long version) {
    }

    public record ReturnLineRequest(
            @NotNull Long saleItemId,
            @NotNull BigDecimal quantity,
            @Size(max = 500) List<@NotBlank @Size(max = 64) String> serials
    ) {
    }

    public record SaleReturnRequest(
            @NotNull Long saleId,
            @NotNull Long saleVersion,
            @NotNull LocalDate returnDate,
            @NotBlank @Size(max = 500) String reason,
            @NotNull RefundMethod refundMethod,
            @Size(max = 1000) String notes,
            @NotEmpty @Valid List<ReturnLineRequest> items
    ) {
    }

    public record SaleSummary(
            Long id,
            String invoiceNumber,
            LocalDate invoiceDate,
            Long customerId,
            String customerCode,
            String customerName,
            SalePaymentType paymentType,
            SaleStatus status,
            BigDecimal grandTotal,
            BigDecimal paidAmount,
            BigDecimal balanceAmount,
            Long version
    ) {
    }

    public record CustomerInfo(
            Long id,
            String customerCode,
            String customerName,
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

    public record SaleLineResponse(
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
            BigDecimal sellingPrice,
            BigDecimal discount,
            BigDecimal tax,
            BigDecimal taxAmount,
            BigDecimal total,
            BigDecimal returnedQuantity,
            BigDecimal remainingQuantity,
            List<String> serials
    ) {
    }

    public record TenderInfo(
            Long id,
            int lineNumber,
            SaleTenderMethod method,
            BigDecimal amount,
            String referenceNumber
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
            Long saleItemId,
            String productName,
            BigDecimal quantity,
            BigDecimal sellingPrice,
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
            SaleReturnStatus status,
            String reason,
            RefundMethod refundMethod,
            BigDecimal merchandiseTotal,
            BigDecimal discountReversal,
            BigDecimal taxReversal,
            BigDecimal grandTotal,
            String notes,
            List<ReturnLineResponse> items
    ) {
    }

    public record SaleDetail(
            Long id,
            String invoiceNumber,
            CustomerInfo customer,
            LocalDate invoiceDate,
            SalePaymentType paymentType,
            String referenceNumber,
            String notes,
            SaleStatus status,
            BigDecimal subtotal,
            BigDecimal itemDiscount,
            BigDecimal orderDiscount,
            BigDecimal totalDiscount,
            BigDecimal tax,
            BigDecimal additionalCharges,
            BigDecimal roundOff,
            BigDecimal grandTotal,
            BigDecimal paidAmount,
            BigDecimal balanceAmount,
            BigDecimal returnedValue,
            BigDecimal refundedAmount,
            boolean editable,
            boolean cancellable,
            boolean returnable,
            List<SaleLineResponse> items,
            List<TenderInfo> tenders,
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
            Long saleId,
            String invoiceNumber,
            Long customerId,
            String customerName,
            RefundMethod refundMethod,
            BigDecimal grandTotal,
            SaleReturnStatus status
    ) {
    }

    public record ReturnDetail(
            Long id,
            String returnNumber,
            Long saleId,
            String invoiceNumber,
            CustomerInfo customer,
            LocalDate returnDate,
            String reason,
            RefundMethod refundMethod,
            String notes,
            SaleReturnStatus status,
            BigDecimal merchandiseTotal,
            BigDecimal discountReversal,
            BigDecimal taxReversal,
            BigDecimal grandTotal,
            List<ReturnLineResponse> items,
            List<StockImpact> stockImpact,
            Instant createdAt,
            String createdBy,
            Long version
    ) {
    }
}
