package com.aveline.bizflow.system.dto;

public record CommercialDefaultsResponse(
        String displayName,
        String currencyCode,
        String locale,
        String dateFormat,
        int decimalPrecision,
        String roundingRule,
        String defaultPaymentMethod
) {
}
