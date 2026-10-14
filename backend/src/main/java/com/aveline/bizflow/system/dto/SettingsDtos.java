package com.aveline.bizflow.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SettingsDtos {

    private SettingsDtos() {
    }

    public record BusinessSettingsResponse(
            String companyName,
            String companyAddress,
            String phone,
            String email,
            String taxName,
            String taxNumber,
            String currencyCode,
            String invoicePrefix,
            String purchasePrefix,
            String returnPrefix,
            String purchaseReturnPrefix,
            String roundingRule,
            boolean allowNegativeStock,
            String defaultPaymentMethod,
            String dateFormat,
            int decimalPrecision,
            long version) {
    }

    public record BusinessSettingsRequest(
            @NotBlank @Size(max = 160) String companyName,
            @Size(max = 500) String companyAddress,
            @Size(max = 32) String phone,
            @Size(max = 160) String email,
            @Size(max = 80) String taxName,
            @Size(max = 64) String taxNumber,
            @NotBlank @Size(min = 3, max = 3) String currencyCode,
            @NotBlank @Size(min = 2, max = 8) String invoicePrefix,
            @NotBlank @Size(min = 2, max = 8) String purchasePrefix,
            @NotBlank @Size(min = 2, max = 8) String returnPrefix,
            @NotBlank @Size(min = 2, max = 8) String purchaseReturnPrefix,
            @NotBlank @Size(max = 32) String roundingRule,
            @NotNull Boolean allowNegativeStock,
            @NotBlank @Size(max = 32) String defaultPaymentMethod,
            @NotBlank @Size(max = 32) String dateFormat,
            @NotNull @Min(0) @Max(4) Integer decimalPrecision,
            @NotNull @Min(0) Long version) {
    }

    public record LetterheadResponse(
            String companyName,
            String companyAddress,
            String phone,
            String email,
            String taxName,
            String taxNumber,
            String currencyCode,
            String dateFormat,
            int decimalPrecision) {
    }
}
