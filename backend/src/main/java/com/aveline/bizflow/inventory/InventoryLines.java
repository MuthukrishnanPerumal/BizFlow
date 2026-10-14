package com.aveline.bizflow.inventory;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.master.StockType;
import com.aveline.bizflow.master.catalog.Product;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class InventoryLines {

    private static final Pattern SERIAL = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");

    private InventoryLines() {
    }

    static String batch(Product product, String raw) {
        boolean tracked = product.getStockType() == StockType.BATCH || product.isBatchTracking();
        return InventoryQuantities.batchCode(raw, tracked);
    }

    static LocalDate expiry(Product product, LocalDate expiry) {
        if (product.isExpiryTracking()) {
            if (expiry == null) {
                throw new BusinessRuleException("EXPIRY", "expiryDate", "Expiry date is required for " + product.getName());
            }
            return expiry;
        }
        if (expiry != null) {
            throw new BusinessRuleException("EXPIRY", "expiryDate", product.getName() + " does not track expiry");
        }
        return null;
    }

    static List<String> serials(Product product, BigDecimal quantity, List<String> raw) {
        List<String> provided = raw == null ? List.of() : raw;
        if (!product.isSerialTracking()) {
            if (!provided.isEmpty()) {
                throw new BusinessRuleException("SERIAL", "serials", product.getName() + " does not use serial numbers");
            }
            return List.of();
        }
        int expected;
        try {
            expected = quantity.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException ex) {
            throw new BusinessRuleException("SERIAL", "serials", "Serial products must use whole quantities");
        }
        if (provided.size() != expected) {
            throw new BusinessRuleException("SERIAL", "serials", "Enter one serial number for each unit");
        }
        Set<String> seen = new HashSet<>();
        List<String> normalized = new ArrayList<>();
        for (String serial : provided) {
            String value = serial.trim().toUpperCase(Locale.ROOT);
            if (!SERIAL.matcher(value).matches()) {
                throw new BusinessRuleException(
                        "SERIAL",
                        "serials",
                        "Serial numbers must be 1 to 64 letters, numbers, dots, underscores, or hyphens");
            }
            if (!seen.add(value)) {
                throw new BusinessRuleException("SERIAL", "serials", "Serial " + value + " is repeated");
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }
}
