package com.aveline.bizflow.master;

import com.aveline.bizflow.common.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Pattern;

public final class MasterText {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,31}$");
    private static final Pattern PRODUCT_CODE = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{1,63}$");
    private static final Pattern BARCODE = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,63}$");
    private static final Pattern SHORT_NAME = Pattern.compile("^[A-Z0-9]{1,16}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+()\\-\\s]{7,32}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern TAX = Pattern.compile("^[A-Z0-9]{5,32}$");
    private static final Pattern PINCODE = Pattern.compile("^[A-Z0-9][A-Z0-9 -]{2,15}$");

    private MasterText() {
    }

    public static String code(String raw, String field) {
        String value = upper(raw);
        if (!CODE.matcher(value).matches()) {
            throw new BusinessRuleException(
                    "INVALID_CODE",
                    field,
                    "Use 2 to 32 letters, numbers, underscores, or hyphens");
        }
        return value;
    }

    public static String productCode(String raw) {
        String value = upper(raw);
        if (!PRODUCT_CODE.matcher(value).matches()) {
            throw new BusinessRuleException(
                    "INVALID_CODE",
                    "productCode",
                    "Product code must be 2 to 64 characters using letters, numbers, dots, underscores, or hyphens");
        }
        return value;
    }

    public static String barcode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = upper(raw);
        if (!BARCODE.matcher(value).matches()) {
            throw new BusinessRuleException(
                    "INVALID_BARCODE",
                    "barcode",
                    "Barcode may contain letters, numbers, dots, underscores, or hyphens");
        }
        return value;
    }

    public static String shortName(String raw) {
        String value = upper(raw);
        if (!SHORT_NAME.matcher(value).matches()) {
            throw new BusinessRuleException(
                    "INVALID_SHORT_NAME",
                    "shortName",
                    "Short name must be 1 to 16 letters or numbers");
        }
        return value;
    }

    public static String required(String raw, int max, String field, String label) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleException("REQUIRED", field, label + " is required");
        }
        String value = collapse(raw);
        if (value.length() > max) {
            throw new BusinessRuleException("TOO_LONG", field, label + " must be " + max + " characters or fewer");
        }
        return value;
    }

    public static String optional(String raw, int max, String field, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = collapse(raw);
        if (value.length() > max) {
            throw new BusinessRuleException("TOO_LONG", field, label + " must be " + max + " characters or fewer");
        }
        return value;
    }

    public static String phone(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = collapse(raw);
        if (!PHONE.matcher(value).matches()) {
            throw new BusinessRuleException("INVALID_PHONE", field, "Enter a valid phone number");
        }
        return value;
    }

    public static String email(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 254 || !EMAIL.matcher(value).matches()) {
            throw new BusinessRuleException("INVALID_EMAIL", "email", "Enter a valid email address");
        }
        return value;
    }

    public static String taxNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = upper(raw).replace(" ", "");
        if (!TAX.matcher(value).matches()) {
            throw new BusinessRuleException(
                    "INVALID_TAX_NUMBER",
                    "taxNumber",
                    "Tax number must be 5 to 32 letters or numbers");
        }
        return value;
    }

    public static String pincode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = upper(raw);
        if (!PINCODE.matcher(value).matches()) {
            throw new BusinessRuleException("INVALID_PINCODE", "pincode", "Enter a valid pincode");
        }
        return value;
    }

    public static BigDecimal money(BigDecimal value, String field, String label, boolean required) {
        if (value == null) {
            if (required) {
                throw new BusinessRuleException("REQUIRED", field, label + " is required");
            }
            return null;
        }
        try {
            return value.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new BusinessRuleException("SCALE", field, label + " supports up to 4 decimal places");
        }
    }

    public static BigDecimal taxPercent(BigDecimal value) {
        BigDecimal scaled = money(value, "taxPercent", "Tax", true);
        if (scaled.compareTo(BigDecimal.ZERO) < 0 || scaled.compareTo(new BigDecimal("100.0000")) > 0) {
            throw new BusinessRuleException("TAX_RANGE", "taxPercent", "Tax must be between 0 and 100 percent");
        }
        return scaled;
    }

    private static String upper(String raw) {
        if (raw == null) {
            return "";
        }
        return collapse(raw).toUpperCase(Locale.ROOT);
    }

    private static String collapse(String raw) {
        return raw.trim().replaceAll("\\s+", " ");
    }
}
