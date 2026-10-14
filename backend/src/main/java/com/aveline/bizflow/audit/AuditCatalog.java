package com.aveline.bizflow.audit;

import java.util.Locale;

public final class AuditCatalog {

    private AuditCatalog() {
    }

    public static String module(String eventType, String resourceType) {
        String event = upper(eventType);
        String resource = upper(resourceType);
        if (event.startsWith("AUTH_") || "LOGIN".equals(event) || "LOGOUT".equals(event)) {
            return "AUTH";
        }
        if (event.startsWith("APPLICATION_") || "SYSTEM".equals(resource)) {
            return "SYSTEM";
        }
        if ("SETTING".equals(resource) || "SETTINGS".equals(resource) || event.startsWith("SETTINGS_")) {
            return "SETTINGS";
        }
        return switch (resource) {
            case "PURCHASE" -> "PURCHASE";
            case "SALE" -> "SALES";
            case "PAYMENT", "EXPENSE" -> "FINANCE";
            case "STOCK_ADJUSTMENT", "STOCK_TRANSFER", "INVENTORY" -> "INVENTORY";
            case "PRODUCT", "CATEGORY", "BRAND", "UNIT" -> "MASTER";
            case "CUSTOMER", "SUPPLIER" -> "PARTY";
            case "USER", "ROLE" -> "IDENTITY";
            default -> fromEvent(event);
        };
    }

    private static String fromEvent(String event) {
        if (event.startsWith("PURCHASE")) {
            return "PURCHASE";
        }
        if (event.startsWith("SALE")) {
            return "SALES";
        }
        if (event.startsWith("PAYMENT") || event.startsWith("EXPENSE")) {
            return "FINANCE";
        }
        if (event.startsWith("STOCK") || event.startsWith("INVENTORY")) {
            return "INVENTORY";
        }
        if (event.startsWith("PRODUCT") || event.startsWith("CATEGORY") || event.startsWith("BRAND") || event.startsWith("UNIT")) {
            return "MASTER";
        }
        if (event.startsWith("CUSTOMER") || event.startsWith("SUPPLIER")) {
            return "PARTY";
        }
        if (event.startsWith("USER") || event.startsWith("ROLE")) {
            return "IDENTITY";
        }
        return "GENERAL";
    }

    private static String upper(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
