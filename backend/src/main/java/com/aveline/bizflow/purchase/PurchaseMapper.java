package com.aveline.bizflow.purchase;

import com.aveline.bizflow.audit.AuditEvent;
import com.aveline.bizflow.finance.Payment;
import com.aveline.bizflow.inventory.StockLedgerEntry;
import com.aveline.bizflow.master.party.Supplier;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.AuditInfo;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.PaymentInfo;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.PurchaseDetail;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.PurchaseLineResponse;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.PurchaseSummary;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.ReturnDetail;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.ReturnInfo;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.ReturnLineResponse;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.ReturnSummary;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.StockImpact;
import com.aveline.bizflow.purchase.dto.PurchaseDtos.SupplierInfo;

import java.math.BigDecimal;
import java.util.List;

public final class PurchaseMapper {

    private PurchaseMapper() {
    }

    public static PurchaseSummary toSummary(Purchase purchase) {
        Supplier supplier = purchase.getSupplier();
        return new PurchaseSummary(
                purchase.getId(),
                purchase.getPurchaseNumber(),
                purchase.getInvoiceNumber(),
                purchase.getPurchaseDate(),
                supplier.getId(),
                supplier.getSupplierCode(),
                supplier.getSupplierName(),
                purchase.getPaymentType(),
                purchase.getStatus(),
                purchase.getGrandTotal(),
                purchase.getPaidAmount(),
                purchase.getBalanceAmount(),
                purchase.getVersion());
    }

    public static PurchaseDetail toDetail(
            Purchase purchase,
            BigDecimal supplierBalance,
            List<Payment> payments,
            List<StockLedgerEntry> stock,
            List<PurchaseReturn> returns,
            List<AuditEvent> audits) {
        boolean returned = hasReturns(purchase);
        return new PurchaseDetail(
                purchase.getId(),
                purchase.getPurchaseNumber(),
                supplier(purchase.getSupplier(), supplierBalance),
                purchase.getInvoiceNumber(),
                purchase.getPurchaseDate(),
                purchase.getPaymentType(),
                purchase.getReferenceNumber(),
                purchase.getNotes(),
                purchase.getStatus(),
                purchase.getSubtotal(),
                purchase.getItemDiscount(),
                purchase.getBillDiscount(),
                purchase.getTotalDiscount(),
                purchase.getTaxAmount(),
                purchase.getAdditionalCharges(),
                purchase.getRoundOff(),
                purchase.getGrandTotal(),
                purchase.getPaidAmount(),
                purchase.getBalanceAmount(),
                purchase.getReturnedValue(),
                !returned && (purchase.getStatus() == PurchaseStatus.DRAFT
                        || purchase.getStatus() == PurchaseStatus.CONFIRMED
                        || purchase.getStatus() == PurchaseStatus.COMPLETED),
                !returned && (purchase.getStatus() == PurchaseStatus.DRAFT
                        || purchase.getStatus() == PurchaseStatus.CONFIRMED
                        || purchase.getStatus() == PurchaseStatus.COMPLETED),
                purchase.getStatus() == PurchaseStatus.CONFIRMED
                        || purchase.getStatus() == PurchaseStatus.COMPLETED
                        || purchase.getStatus() == PurchaseStatus.PARTIALLY_RETURNED,
                purchase.getItems().stream().map(PurchaseMapper::line).toList(),
                payments.stream().map(PurchaseMapper::payment).toList(),
                stock.stream().map(PurchaseMapper::stock).toList(),
                returns.stream().map(PurchaseMapper::returnInfo).toList(),
                audits.stream().map(PurchaseMapper::audit).toList(),
                purchase.getCreatedAt(),
                purchase.getCreatedBy(),
                purchase.getVersion());
    }

    public static ReturnSummary toReturnSummary(PurchaseReturn purchaseReturn) {
        return new ReturnSummary(
                purchaseReturn.getId(),
                purchaseReturn.getReturnNumber(),
                purchaseReturn.getReturnDate(),
                purchaseReturn.getPurchase().getId(),
                purchaseReturn.getPurchase().getPurchaseNumber(),
                purchaseReturn.getPurchase().getInvoiceNumber(),
                purchaseReturn.getSupplier().getId(),
                purchaseReturn.getSupplier().getSupplierName(),
                purchaseReturn.getGrandTotal(),
                purchaseReturn.getStatus());
    }

    public static ReturnDetail toReturnDetail(
            PurchaseReturn purchaseReturn,
            BigDecimal supplierBalance,
            List<StockLedgerEntry> stock) {
        return new ReturnDetail(
                purchaseReturn.getId(),
                purchaseReturn.getReturnNumber(),
                purchaseReturn.getPurchase().getId(),
                purchaseReturn.getPurchase().getPurchaseNumber(),
                purchaseReturn.getPurchase().getInvoiceNumber(),
                supplier(purchaseReturn.getSupplier(), supplierBalance),
                purchaseReturn.getReturnDate(),
                purchaseReturn.getNotes(),
                purchaseReturn.getStatus(),
                purchaseReturn.getMerchandiseTotal(),
                purchaseReturn.getGrandTotal(),
                purchaseReturn.getItems().stream().map(PurchaseMapper::returnLine).toList(),
                stock.stream().map(PurchaseMapper::stock).toList(),
                purchaseReturn.getCreatedAt(),
                purchaseReturn.getCreatedBy(),
                purchaseReturn.getVersion());
    }

    private static PurchaseLineResponse line(PurchaseItem item) {
        return new PurchaseLineResponse(
                item.getId(),
                item.getLineNumber(),
                item.getProduct().getId(),
                item.getProduct().getProductCode(),
                item.getProduct().getName(),
                item.getProduct().getUnit().getShortName(),
                item.getProduct().isBatchTracking(),
                item.getProduct().isSerialTracking(),
                item.getProduct().isExpiryTracking(),
                item.getProduct().getUnit().isDecimalAllowed(),
                item.getBarcode(),
                item.getBatchCode(),
                item.getExpiryDate(),
                item.getQuantity(),
                item.getFreeQuantity(),
                item.getPurchasePrice(),
                item.getDiscountAmount(),
                item.getTaxPercent(),
                item.getTaxAmount(),
                item.getLineTotal(),
                item.getReturnedQuantity(),
                item.getReturnedFreeQuantity(),
                item.remainingQuantity(),
                item.remainingFreeQuantity(),
                item.getSerials().stream().map(PurchaseItemSerial::getSerialNumber).toList());
    }

    private static ReturnInfo returnInfo(PurchaseReturn purchaseReturn) {
        return new ReturnInfo(
                purchaseReturn.getId(),
                purchaseReturn.getReturnNumber(),
                purchaseReturn.getReturnDate(),
                purchaseReturn.getStatus(),
                purchaseReturn.getMerchandiseTotal(),
                purchaseReturn.getGrandTotal(),
                purchaseReturn.getNotes(),
                purchaseReturn.getItems().stream().map(PurchaseMapper::returnLine).toList());
    }

    private static ReturnLineResponse returnLine(PurchaseReturnItem item) {
        return new ReturnLineResponse(
                item.getId(),
                item.getPurchaseItem().getId(),
                item.getPurchaseItem().getProduct().getName(),
                item.getQuantity(),
                item.getFreeQuantity(),
                item.getPurchasePrice(),
                item.getDiscountAmount(),
                item.getTaxAmount(),
                item.getLineTotal(),
                item.getSerials().stream().map(PurchaseReturnSerial::getSerialNumber).toList());
    }

    private static PaymentInfo payment(Payment payment) {
        return new PaymentInfo(
                payment.getId(),
                payment.getPaymentNumber(),
                payment.getPaymentDate(),
                payment.getPaymentMethod(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getReferenceNumber());
    }

    private static StockImpact stock(StockLedgerEntry entry) {
        return new StockImpact(
                entry.getId(),
                entry.getEntryDate(),
                entry.getProduct().getId(),
                entry.getProduct().getName(),
                entry.getTransactionType(),
                entry.getReferenceType(),
                entry.getReferenceId(),
                entry.getBatchCode(),
                entry.getExpiryDate(),
                entry.getQuantityIn(),
                entry.getQuantityOut(),
                entry.getRunningBalance(),
                entry.getUnitCost(),
                entry.getTotalValue(),
                entry.getCreatedBy(),
                entry.getRemarks());
    }

    private static AuditInfo audit(AuditEvent event) {
        return new AuditInfo(
                event.getId(),
                event.getEventType(),
                event.getAction(),
                event.getActor(),
                event.getOutcome().name(),
                event.getDetail(),
                event.getCreatedAt());
    }

    private static SupplierInfo supplier(Supplier supplier, BigDecimal balance) {
        return new SupplierInfo(
                supplier.getId(),
                supplier.getSupplierCode(),
                supplier.getSupplierName(),
                supplier.getCompanyName(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getTaxNumber(),
                supplier.getAddress(),
                supplier.getCity(),
                supplier.getState(),
                balance);
    }

    private static boolean hasReturns(Purchase purchase) {
        return purchase.getItems().stream().anyMatch(item ->
                item.getReturnedQuantity().signum() > 0 || item.getReturnedFreeQuantity().signum() > 0);
    }
}
