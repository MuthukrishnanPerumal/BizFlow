package com.aveline.bizflow.inventory;

import com.aveline.bizflow.common.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Pattern;

public final class InventoryQuantities {

    private static final Pattern BATCH = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");

    private InventoryQuantities() {
    }

    public static BigDecimal quantity(BigDecimal value, boolean decimalAllowed, String field, String label) {
        if (value == null) {
            throw new BusinessRuleException("REQUIRED", field, label + " is required");
        }
        if (value.signum() <= 0) {
            throw new BusinessRuleException("QUANTITY", field, label + " must be greater than zero");
        }
        if (value.stripTrailingZeros().scale() > 4) {
            throw new BusinessRuleException("SCALE", field, label + " supports up to 4 decimal places");
        }
        BigDecimal scaled = value.setScale(4, RoundingMode.HALF_UP);
        if (!decimalAllowed && scaled.stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleException("WHOLE_QUANTITY", field, label + " must be a whole number for this unit");
        }
        return scaled;
    }

    public static BigDecimal money(BigDecimal value, String field, String label) {
        if (value == null) {
            throw new BusinessRuleException("REQUIRED", field, label + " is required");
        }
        if (value.signum() < 0) {
            throw new BusinessRuleException("NEGATIVE_AMOUNT", field, label + " cannot be negative");
        }
        if (value.stripTrailingZeros().scale() > 4) {
            throw new BusinessRuleException("SCALE", field, label + " supports up to 4 decimal places");
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    public static String batchCode(String raw, boolean tracked) {
        if (!tracked) {
            if (raw != null && !raw.isBlank()) {
                throw new BusinessRuleException("BATCH", "batch", "This product does not use batches");
            }
            return "";
        }
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleException("BATCH", "batch", "Batch is required");
        }
        String value = raw.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (!BATCH.matcher(value).matches()) {
            throw new BusinessRuleException(
                    "BATCH",
                    "batch",
                    "Batch must be 1 to 64 letters, numbers, dots, underscores, or hyphens");
        }
        return value;
    }

    public static String remarks(String primary, String notes) {
        String value = notes == null || notes.isBlank() ? primary : primary + ". " + notes;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
