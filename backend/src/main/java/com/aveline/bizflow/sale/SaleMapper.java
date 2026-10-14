package com.aveline.bizflow.sale;

import com.aveline.bizflow.audit.AuditEvent;
import com.aveline.bizflow.finance.Payment;
import com.aveline.bizflow.inventory.StockLedgerEntry;
import com.aveline.bizflow.master.party.Customer;
import com.aveline.bizflow.sale.dto.SaleDtos.AuditInfo;
import com.aveline.bizflow.sale.dto.SaleDtos.CustomerInfo;
import com.aveline.bizflow.sale.dto.SaleDtos.PaymentInfo;
import com.aveline.bizflow.sale.dto.SaleDtos.ReturnDetail;
import com.aveline.bizflow.sale.dto.SaleDtos.ReturnInfo;
import com.aveline.bizflow.sale.dto.SaleDtos.ReturnLineResponse;
import com.aveline.bizflow.sale.dto.SaleDtos.ReturnSummary;
import com.aveline.bizflow.sale.dto.SaleDtos.SaleDetail;
import com.aveline.bizflow.sale.dto.SaleDtos.SaleLineResponse;
import com.aveline.bizflow.sale.dto.SaleDtos.SaleSummary;
import com.aveline.bizflow.sale.dto.SaleDtos.StockImpact;
import com.aveline.bizflow.sale.dto.SaleDtos.TenderInfo;

import java.math.BigDecimal;
import java.util.List;

public final class SaleMapper {

    private SaleMapper() {
    }

    public static SaleSummary toSummary(Sale sale) {
        Customer customer = sale.getCustomer();
        return new SaleSummary(
                sale.getId(),
                sale.getSaleNumber(),
                sale.getInvoiceDate(),
                customer.getId(),
                customer.getCustomerCode(),
                customer.getCustomerName(),
                sale.getPaymentType(),
                sale.getStatus(),
                sale.getGrandTotal(),
                sale.getPaidAmount(),
                sale.getBalanceAmount(),
                sale.getVersion());
    }

    public static SaleDetail toDetail(
            Sale sale,
            BigDecimal customerBalance,
            List<Payment> payments,
            List<StockLedgerEntry> stock,
            List<SaleReturn> returns,
            List<AuditEvent> audits) {
        boolean returned = hasReturns(sale);
        boolean openPosted = sale.getStatus() == SaleStatus.CONFIRMED || sale.getStatus() == SaleStatus.COMPLETED;
        return new SaleDetail(
                sale.getId(),
                sale.getSaleNumber(),
                customer(sale.getCustomer(), customerBalance),
                sale.getInvoiceDate(),
                sale.getPaymentType(),
                sale.getReferenceNumber(),
                sale.getNotes(),
                sale.getStatus(),
                sale.getSubtotal(),
                sale.getItemDiscount(),
                sale.getOrderDiscount(),
                sale.getTotalDiscount(),
                sale.getTaxAmount(),
                sale.getAdditionalCharges(),
                sale.getRoundOff(),
                sale.getGrandTotal(),
                sale.getPaidAmount(),
                sale.getBalanceAmount(),
                sale.getReturnedValue(),
                sale.getRefundedAmount(),
                !returned && (sale.getStatus() == SaleStatus.DRAFT || openPosted),
                !returned && (sale.getStatus() == SaleStatus.DRAFT || openPosted),
                sale.getStatus() == SaleStatus.CONFIRMED
                        || sale.getStatus() == SaleStatus.COMPLETED
                        || sale.getStatus() == SaleStatus.PARTIALLY_RETURNED,
                sale.getItems().stream().map(SaleMapper::line).toList(),
                sale.getTenders().stream().map(SaleMapper::tender).toList(),
                payments.stream().map(SaleMapper::payment).toList(),
                stock.stream().map(SaleMapper::stock).toList(),
                returns.stream().map(SaleMapper::returnInfo).toList(),
                audits.stream().map(SaleMapper::audit).toList(),
                sale.getCreatedAt(),
                sale.getCreatedBy(),
                sale.getVersion());
    }

    public static ReturnSummary toReturnSummary(SaleReturn saleReturn) {
        return new ReturnSummary(
                saleReturn.getId(),
                saleReturn.getReturnNumber(),
                saleReturn.getReturnDate(),
                saleReturn.getSale().getId(),
                saleReturn.getSale().getSaleNumber(),
                saleReturn.getCustomer().getId(),
                saleReturn.getCustomer().getCustomerName(),
                saleReturn.getRefundMethod(),
                saleReturn.getGrandTotal(),
                saleReturn.getStatus());
    }

    public static ReturnDetail toReturnDetail(SaleReturn saleReturn, BigDecimal customerBalance, List<StockLedgerEntry> stock) {
        return new ReturnDetail(
                saleReturn.getId(),
                saleReturn.getReturnNumber(),
                saleReturn.getSale().getId(),
                saleReturn.getSale().getSaleNumber(),
                customer(saleReturn.getCustomer(), customerBalance),
                saleReturn.getReturnDate(),
                saleReturn.getReason(),
                saleReturn.getRefundMethod(),
                saleReturn.getNotes(),
                saleReturn.getStatus(),
                saleReturn.getMerchandiseTotal(),
                saleReturn.getItemDiscountReversal().add(saleReturn.getOrderDiscountReversal()),
                saleReturn.getTaxReversal(),
                saleReturn.getGrandTotal(),
                saleReturn.getItems().stream().map(SaleMapper::returnLine).toList(),
                stock.stream().map(SaleMapper::stock).toList(),
                saleReturn.getCreatedAt(),
                saleReturn.getCreatedBy(),
                saleReturn.getVersion());
    }

    private static SaleLineResponse line(SaleItem item) {
        return new SaleLineResponse(
                item.getId(),
                item.getLineNumber(),
                item.getProduct().getId(),
                item.getProduct().getProductCode(),
                item.getProduct().getName(),
                item.getProduct().getUnit().getName(),
                item.getProduct().isBatchTracking(),
                item.getProduct().isSerialTracking(),
                item.getProduct().isExpiryTracking(),
                item.getProduct().getUnit().isDecimalAllowed(),
                item.getBarcode(),
                item.getBatchCode(),
                item.getExpiryDate(),
                item.getQuantity(),
                item.getSellingPrice(),
                item.getDiscountAmount(),
                item.getTaxPercent(),
                item.getTaxAmount(),
                item.getLineTotal(),
                item.getReturnedQuantity(),
                item.remainingQuantity(),
                item.getSerials().stream().filter(serial -> !serial.isReturned()).map(SaleItemSerial::getSerialNumber).toList());
    }

    private static TenderInfo tender(SaleTender tender) {
        return new TenderInfo(
                tender.getId(),
                tender.getLineNumber(),
                tender.getPaymentMethod(),
                tender.getAmount(),
                tender.getReferenceNumber());
    }

    private static ReturnInfo returnInfo(SaleReturn saleReturn) {
        return new ReturnInfo(
                saleReturn.getId(),
                saleReturn.getReturnNumber(),
                saleReturn.getReturnDate(),
                saleReturn.getStatus(),
                saleReturn.getReason(),
                saleReturn.getRefundMethod(),
                saleReturn.getMerchandiseTotal(),
                saleReturn.getItemDiscountReversal().add(saleReturn.getOrderDiscountReversal()),
                saleReturn.getTaxReversal(),
                saleReturn.getGrandTotal(),
                saleReturn.getNotes(),
                saleReturn.getItems().stream().map(SaleMapper::returnLine).toList());
    }

    private static ReturnLineResponse returnLine(SaleReturnItem item) {
        return new ReturnLineResponse(
                item.getId(),
                item.getSaleItem().getId(),
                item.getSaleItem().getProduct().getName(),
                item.getQuantity(),
                item.getSellingPrice(),
                item.getDiscountAmount(),
                item.getTaxAmount(),
                item.getLineTotal(),
                item.getSerials().stream().map(SaleReturnSerial::getSerialNumber).toList());
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

    private static CustomerInfo customer(Customer customer, BigDecimal balance) {
        return new CustomerInfo(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getCustomerName(),
                customer.getCompanyName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getTaxNumber(),
                customer.getAddress(),
                customer.getCity(),
                customer.getState(),
                balance);
    }

    private static boolean hasReturns(Sale sale) {
        return sale.getItems().stream().anyMatch(item -> item.getReturnedQuantity().signum() > 0);
    }
}
