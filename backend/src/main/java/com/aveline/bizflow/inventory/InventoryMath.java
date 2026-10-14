package com.aveline.bizflow.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class InventoryMath {

    public static final BigDecimal ZERO = new BigDecimal("0.0000");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private InventoryMath() {
    }

    public static BigDecimal available(BigDecimal quantityOnHand, BigDecimal reservedQuantity) {
        return scale(quantityOnHand.subtract(reservedQuantity));
    }

    public static BigDecimal stockValue(BigDecimal quantity, BigDecimal unitCost) {
        return quantity.multiply(unitCost).setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal weightedAverage(
            BigDecimal oldQuantity,
            BigDecimal oldUnitCost,
            BigDecimal incomingQuantity,
            BigDecimal incomingValue) {
        BigDecimal newQuantity = oldQuantity.add(incomingQuantity);
        if (newQuantity.signum() == 0) {
            return ZERO;
        }
        BigDecimal oldValue = oldQuantity.multiply(oldUnitCost);
        return oldValue.add(incomingValue).divide(newQuantity, 4, RoundingMode.HALF_UP);
    }

    public static BigDecimal shortage(BigDecimal availableQuantity, BigDecimal reorderLevel) {
        BigDecimal gap = reorderLevel.subtract(availableQuantity);
        return gap.signum() > 0 ? scale(gap) : ZERO;
    }

    public static StockStatus status(BigDecimal availableQuantity, BigDecimal reorderLevel, BigDecimal maximumStockLevel) {
        if (availableQuantity.signum() <= 0) {
            return StockStatus.OUT_OF_STOCK;
        }
        if (maximumStockLevel != null && maximumStockLevel.signum() > 0 && availableQuantity.compareTo(maximumStockLevel) > 0) {
            return StockStatus.OVER_STOCK;
        }
        if (availableQuantity.compareTo(reorderLevel) <= 0) {
            return StockStatus.LOW_STOCK;
        }
        return StockStatus.IN_STOCK;
    }

    public static BigDecimal healthPercent(long inStock, long trackedProducts) {
        if (trackedProducts <= 0) {
            return new BigDecimal("0.00");
        }
        return BigDecimal.valueOf(inStock)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(trackedProducts), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal scale(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
