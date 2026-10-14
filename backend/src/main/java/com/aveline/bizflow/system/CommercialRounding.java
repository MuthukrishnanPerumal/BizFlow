package com.aveline.bizflow.system;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CommercialRounding {

    public enum Rule {
        NONE,
        NEAREST_UNIT,
        NEAREST_TENTH;

        public static Rule parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return NONE;
            }
            try {
                return Rule.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return NONE;
            }
        }
    }

    private CommercialRounding() {
    }

    public static BigDecimal apply(BigDecimal amount, int precision, Rule rule) {
        if (amount == null) {
            return null;
        }
        int scale = Math.min(Math.max(precision, 0), 4);
        Rule selected = rule == null ? Rule.NONE : rule;
        BigDecimal rounded = switch (selected) {
            case NEAREST_UNIT -> amount.setScale(0, RoundingMode.HALF_UP);
            case NEAREST_TENTH -> amount.setScale(1, RoundingMode.HALF_UP);
            case NONE -> amount;
        };
        return rounded.setScale(scale, RoundingMode.HALF_UP);
    }
}
