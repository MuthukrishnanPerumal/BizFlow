package com.aveline.bizflow.finance;

import java.time.LocalDate;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaymentTerms {

    private static final Pattern DAYS = Pattern.compile("(\\d+)");

    private PaymentTerms() {
    }

    public static OptionalInt creditDays(String terms) {
        if (terms == null || terms.isBlank()) {
            return OptionalInt.empty();
        }
        Matcher matcher = DAYS.matcher(terms);
        if (!matcher.find()) {
            return OptionalInt.empty();
        }
        try {
            int days = Integer.parseInt(matcher.group(1));
            if (days < 0 || days > 3650) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(days);
        } catch (NumberFormatException ex) {
            return OptionalInt.empty();
        }
    }

    public static LocalDate dueDate(LocalDate documentDate, String terms) {
        OptionalInt days = creditDays(terms);
        if (days.isEmpty() || documentDate == null) {
            return null;
        }
        return documentDate.plusDays(days.getAsInt());
    }

    public static boolean overdue(LocalDate documentDate, String terms, LocalDate asOf) {
        LocalDate due = dueDate(documentDate, terms);
        return due != null && asOf != null && due.isBefore(asOf);
    }
}
