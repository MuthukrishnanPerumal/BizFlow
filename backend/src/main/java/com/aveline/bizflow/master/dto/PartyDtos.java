package com.aveline.bizflow.master.dto;

import com.aveline.bizflow.master.LedgerEntryType;
import com.aveline.bizflow.master.RecordStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class PartyDtos {

    private PartyDtos() {
    }

    public record AccountSummary(BigDecimal debitTotal, BigDecimal creditTotal, BigDecimal balance) {
    }

    public record LedgerEntryResponse(
            Long id,
            LedgerEntryType entryType,
            String referenceType,
            String referenceId,
            LocalDate entryDate,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            String narration,
            Instant createdAt
    ) {
    }

    public record SupplierRequest(
            @NotBlank
            @Size(max = 32)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,31}$", message = "Use 2 to 32 letters, numbers, underscores, or hyphens")
            String supplierCode,
            @NotBlank @Size(max = 200) String supplierName,
            @Size(max = 200) String companyName,
            @Size(max = 160) String contactPerson,
            @Size(max = 32) @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,32}$", message = "Enter a valid phone number") String phone,
            @Size(max = 32) @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,32}$", message = "Enter a valid phone number") String alternatePhone,
            @Email @Size(max = 254) String email,
            @Size(max = 32) String taxNumber,
            @Size(max = 500) String address,
            @Size(max = 80) String country,
            @Size(max = 80) String state,
            @Size(max = 80) String city,
            @Size(max = 16) String pincode,
            @NotNull @DecimalMin("-999999999999999.9999") @DecimalMax("999999999999999.9999") @Digits(integer = 15, fraction = 4)
            BigDecimal openingBalance,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal creditLimit,
            @Size(max = 120) String paymentTerms,
            @NotNull RecordStatus status
    ) {
    }

    public record SupplierUpdateRequest(
            @NotBlank
            @Size(max = 32)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,31}$", message = "Use 2 to 32 letters, numbers, underscores, or hyphens")
            String supplierCode,
            @NotBlank @Size(max = 200) String supplierName,
            @Size(max = 200) String companyName,
            @Size(max = 160) String contactPerson,
            @Size(max = 32) @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,32}$", message = "Enter a valid phone number") String phone,
            @Size(max = 32) @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,32}$", message = "Enter a valid phone number") String alternatePhone,
            @Email @Size(max = 254) String email,
            @Size(max = 32) String taxNumber,
            @Size(max = 500) String address,
            @Size(max = 80) String country,
            @Size(max = 80) String state,
            @Size(max = 80) String city,
            @Size(max = 16) String pincode,
            @NotNull @DecimalMin("-999999999999999.9999") @DecimalMax("999999999999999.9999") @Digits(integer = 15, fraction = 4)
            BigDecimal openingBalance,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal creditLimit,
            @Size(max = 120) String paymentTerms,
            @NotNull RecordStatus status,
            @NotNull Long version
    ) {
    }

    public record SupplierResponse(
            Long id,
            String supplierCode,
            String supplierName,
            String companyName,
            String contactPerson,
            String phone,
            String alternatePhone,
            String email,
            String taxNumber,
            String address,
            String country,
            String state,
            String city,
            String pincode,
            BigDecimal openingBalance,
            BigDecimal creditLimit,
            String paymentTerms,
            RecordStatus status,
            Instant createdAt,
            Instant updatedAt,
            Long version,
            AccountSummary account
    ) {
    }

    public record CustomerRequest(
            @NotBlank
            @Size(max = 32)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,31}$", message = "Use 2 to 32 letters, numbers, underscores, or hyphens")
            String customerCode,
            @NotBlank @Size(max = 200) String customerName,
            @Size(max = 200) String companyName,
            @Size(max = 32) @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,32}$", message = "Enter a valid phone number") String phone,
            @Size(max = 32) @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,32}$", message = "Enter a valid phone number") String alternatePhone,
            @Email @Size(max = 254) String email,
            @Size(max = 32) String taxNumber,
            @Size(max = 500) String address,
            @Size(max = 80) String country,
            @Size(max = 80) String state,
            @Size(max = 80) String city,
            @Size(max = 16) String pincode,
            @NotNull @DecimalMin("-999999999999999.9999") @DecimalMax("999999999999999.9999") @Digits(integer = 15, fraction = 4)
            BigDecimal openingBalance,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal creditLimit,
            @Size(max = 120) String paymentTerms,
            @NotNull RecordStatus status
    ) {
    }

    public record CustomerUpdateRequest(
            @NotBlank
            @Size(max = 32)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,31}$", message = "Use 2 to 32 letters, numbers, underscores, or hyphens")
            String customerCode,
            @NotBlank @Size(max = 200) String customerName,
            @Size(max = 200) String companyName,
            @Size(max = 32) @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,32}$", message = "Enter a valid phone number") String phone,
            @Size(max = 32) @Pattern(regexp = "^$|^[0-9+()\\-\\s]{7,32}$", message = "Enter a valid phone number") String alternatePhone,
            @Email @Size(max = 254) String email,
            @Size(max = 32) String taxNumber,
            @Size(max = 500) String address,
            @Size(max = 80) String country,
            @Size(max = 80) String state,
            @Size(max = 80) String city,
            @Size(max = 16) String pincode,
            @NotNull @DecimalMin("-999999999999999.9999") @DecimalMax("999999999999999.9999") @Digits(integer = 15, fraction = 4)
            BigDecimal openingBalance,
            @NotNull @DecimalMin("0.0") @Digits(integer = 15, fraction = 4) BigDecimal creditLimit,
            @Size(max = 120) String paymentTerms,
            @NotNull RecordStatus status,
            @NotNull Long version
    ) {
    }

    public record CustomerResponse(
            Long id,
            String customerCode,
            String customerName,
            String companyName,
            String phone,
            String alternatePhone,
            String email,
            String taxNumber,
            String address,
            String country,
            String state,
            String city,
            String pincode,
            BigDecimal openingBalance,
            BigDecimal creditLimit,
            String paymentTerms,
            RecordStatus status,
            Instant createdAt,
            Instant updatedAt,
            Long version,
            AccountSummary account
    ) {
    }
}
