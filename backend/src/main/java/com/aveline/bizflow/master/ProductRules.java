package com.aveline.bizflow.master;

import com.aveline.bizflow.common.exception.BusinessRuleException;

import java.math.BigDecimal;

public final class ProductRules {

    private ProductRules() {
    }

    public static void prices(
            BigDecimal purchasePrice,
            BigDecimal sellingPrice,
            BigDecimal mrp,
            BigDecimal minimumSellingPrice) {
        nonNegative(purchasePrice, "purchasePrice", "Purchase price");
        nonNegative(sellingPrice, "sellingPrice", "Selling price");
        nonNegative(mrp, "mrp", "MRP");
        nonNegative(minimumSellingPrice, "minimumSellingPrice", "Minimum selling price");
        if (minimumSellingPrice.compareTo(sellingPrice) > 0) {
            throw new BusinessRuleException(
                    "PRICE_RULE",
                    "minimumSellingPrice",
                    "Minimum selling price cannot be above the selling price");
        }
        if (sellingPrice.compareTo(mrp) > 0) {
            throw new BusinessRuleException("PRICE_RULE", "sellingPrice", "Selling price cannot be above MRP");
        }
        if (minimumSellingPrice.compareTo(mrp) > 0) {
            throw new BusinessRuleException(
                    "PRICE_RULE",
                    "minimumSellingPrice",
                    "Minimum selling price cannot be above MRP");
        }
        if (purchasePrice.compareTo(mrp) > 0) {
            throw new BusinessRuleException("PRICE_RULE", "purchasePrice", "Purchase price cannot be above MRP");
        }
    }

    public static void stockLevels(BigDecimal reorderLevel, BigDecimal maximumStockLevel, boolean decimalAllowed) {
        nonNegative(reorderLevel, "reorderLevel", "Reorder level");
        if (maximumStockLevel != null) {
            nonNegative(maximumStockLevel, "maximumStockLevel", "Maximum stock");
            if (maximumStockLevel.compareTo(reorderLevel) < 0) {
                throw new BusinessRuleException(
                        "STOCK_RULE",
                        "maximumStockLevel",
                        "Maximum stock cannot be below the reorder level");
            }
        }
        quantity(reorderLevel, decimalAllowed, "reorderLevel", "Reorder level");
        if (maximumStockLevel != null) {
            quantity(maximumStockLevel, decimalAllowed, "maximumStockLevel", "Maximum stock");
        }
    }

    public static void tracking(StockType stockType, boolean batchTracking, boolean serialTracking, boolean expiryTracking) {
        switch (stockType) {
            case NONE -> rejectTracking(
                    batchTracking || serialTracking || expiryTracking,
                    "Products without stock tracking cannot use batch, serial, or expiry tracking");
            case BATCH -> rejectTracking(
                    !batchTracking || serialTracking,
                    "Batch stock requires batch tracking and cannot use serial tracking");
            case SERIAL -> rejectTracking(
                    !serialTracking || batchTracking,
                    "Serial stock requires serial tracking and cannot use batch tracking");
            case PICS -> rejectTracking(
                    batchTracking || serialTracking || expiryTracking,
                    "Piece stock does not use batch, serial, or expiry tracking");
            default -> throw new BusinessRuleException("STOCK_TYPE", "stockType", "Select a stock type");
        }
    }

    private static void rejectTracking(boolean invalid, String message) {
        if (invalid) {
            throw new BusinessRuleException("STOCK_TYPE", "stockType", message);
        }
    }

    private static void nonNegative(BigDecimal value, String field, String label) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("NEGATIVE_AMOUNT", field, label + " cannot be negative");
        }
    }

    private static void quantity(BigDecimal value, boolean decimalAllowed, String field, String label) {
        if (!decimalAllowed && value.stripTrailingZeros().scale() > 0) {
            throw new BusinessRuleException(
                    "WHOLE_QUANTITY",
                    field,
                    label + " must be a whole number for this unit");
        }
    }
}
