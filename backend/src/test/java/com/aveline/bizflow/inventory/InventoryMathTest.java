package com.aveline.bizflow.inventory;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryMathTest {

    @Test
    void statusFollowsAvailableReorderAndMaximum() {
        BigDecimal reorder = new BigDecimal("5.0000");
        BigDecimal maximum = new BigDecimal("20.0000");
        assertEquals(StockStatus.OUT_OF_STOCK, InventoryMath.status(new BigDecimal("0.0000"), reorder, maximum));
        assertEquals(StockStatus.OUT_OF_STOCK, InventoryMath.status(new BigDecimal("-1.0000"), reorder, maximum));
        assertEquals(StockStatus.LOW_STOCK, InventoryMath.status(new BigDecimal("5.0000"), reorder, maximum));
        assertEquals(StockStatus.LOW_STOCK, InventoryMath.status(new BigDecimal("1.0000"), reorder, maximum));
        assertEquals(StockStatus.IN_STOCK, InventoryMath.status(new BigDecimal("6.0000"), reorder, maximum));
        assertEquals(StockStatus.IN_STOCK, InventoryMath.status(new BigDecimal("20.0000"), reorder, maximum));
        assertEquals(StockStatus.OVER_STOCK, InventoryMath.status(new BigDecimal("21.0000"), reorder, maximum));
        assertEquals(StockStatus.IN_STOCK, InventoryMath.status(new BigDecimal("21.0000"), reorder, null));
        assertEquals(StockStatus.IN_STOCK, InventoryMath.status(new BigDecimal("8.0000"), BigDecimal.ZERO, null));
    }

    @Test
    void weightedAverageAndShortageUseFourDecimalPlaces() {
        BigDecimal average = InventoryMath.weightedAverage(
                new BigDecimal("4.0000"),
                new BigDecimal("10.0000"),
                new BigDecimal("4.0000"),
                new BigDecimal("80.0000"));
        assertEquals(new BigDecimal("15.0000"), average);
        assertEquals(new BigDecimal("3.0000"), InventoryMath.shortage(new BigDecimal("2.0000"), new BigDecimal("5.0000")));
        assertEquals(new BigDecimal("0.0000"), InventoryMath.shortage(new BigDecimal("6.0000"), new BigDecimal("5.0000")));
        assertEquals(new BigDecimal("40.0000"), InventoryMath.stockValue(new BigDecimal("4.0000"), new BigDecimal("10.0000")));
        assertEquals(new BigDecimal("6.0000"), InventoryMath.available(new BigDecimal("8.0000"), new BigDecimal("2.0000")));
        assertEquals(new BigDecimal("50.00"), InventoryMath.healthPercent(1, 2));
    }
}
