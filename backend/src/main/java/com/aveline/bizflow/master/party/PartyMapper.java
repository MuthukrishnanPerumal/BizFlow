package com.aveline.bizflow.master.party;

import com.aveline.bizflow.master.dto.PartyDtos.AccountSummary;
import com.aveline.bizflow.master.dto.PartyDtos.CustomerResponse;
import com.aveline.bizflow.master.dto.PartyDtos.LedgerEntryResponse;
import com.aveline.bizflow.master.dto.PartyDtos.SupplierResponse;

import java.math.BigDecimal;

public final class PartyMapper {

    private PartyMapper() {
    }

    public static SupplierResponse toSupplier(Supplier supplier, AccountSummary account) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getSupplierCode(),
                supplier.getSupplierName(),
                supplier.getCompanyName(),
                supplier.getContactPerson(),
                supplier.getPhone(),
                supplier.getAlternatePhone(),
                supplier.getEmail(),
                supplier.getTaxNumber(),
                supplier.getAddress(),
                supplier.getCountry(),
                supplier.getState(),
                supplier.getCity(),
                supplier.getPincode(),
                supplier.getOpeningBalance(),
                supplier.getCreditLimit(),
                supplier.getPaymentTerms(),
                supplier.getStatus(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt(),
                supplier.getVersion(),
                account);
    }

    public static CustomerResponse toCustomer(Customer customer, AccountSummary account) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getCustomerName(),
                customer.getCompanyName(),
                customer.getPhone(),
                customer.getAlternatePhone(),
                customer.getEmail(),
                customer.getTaxNumber(),
                customer.getAddress(),
                customer.getCountry(),
                customer.getState(),
                customer.getCity(),
                customer.getPincode(),
                customer.getOpeningBalance(),
                customer.getCreditLimit(),
                customer.getPaymentTerms(),
                customer.getStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt(),
                customer.getVersion(),
                account);
    }

    public static LedgerEntryResponse toLedger(SupplierLedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getEntryType(),
                entry.getReferenceType(),
                entry.getReferenceId(),
                entry.getEntryDate(),
                entry.getDebitAmount(),
                entry.getCreditAmount(),
                entry.getNarration(),
                entry.getCreatedAt());
    }

    public static LedgerEntryResponse toLedger(CustomerLedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getEntryType(),
                entry.getReferenceType(),
                entry.getReferenceId(),
                entry.getEntryDate(),
                entry.getDebitAmount(),
                entry.getCreditAmount(),
                entry.getNarration(),
                entry.getCreatedAt());
    }

    public static AccountSummary supplierAccount(BigDecimal debit, BigDecimal credit) {
        return new AccountSummary(debit, credit, credit.subtract(debit));
    }

    public static AccountSummary customerAccount(BigDecimal debit, BigDecimal credit) {
        return new AccountSummary(debit, credit, debit.subtract(credit));
    }
}
