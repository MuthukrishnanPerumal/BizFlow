package com.aveline.bizflow.reporting;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfitMathTest {

    @Test
    void buildsGrossAndNetProfitFromTheSameComponents() {
        ProfitMath.Statement statement = ProfitMath.statement(
                new BigDecimal("80.0000"),
                new BigDecimal("5.0000"),
                new BigDecimal("25.0000"),
                new BigDecimal("4.0000"),
                new BigDecimal("10.0000"));

        assertEquals(new BigDecimal("80.0000"), statement.grossSales());
        assertEquals(new BigDecimal("5.0000"), statement.salesReturns());
        assertEquals(new BigDecimal("75.0000"), statement.revenue());
        assertEquals(new BigDecimal("21.0000"), statement.costOfGoodsSold());
        assertEquals(new BigDecimal("54.0000"), statement.grossProfit());
        assertEquals(new BigDecimal("10.0000"), statement.expenses());
        assertEquals(new BigDecimal("44.0000"), statement.netProfit());
        assertEquals(statement.grossProfit(), ProfitMath.grossProfit(statement.revenue(), statement.costOfGoodsSold()));
        assertEquals(statement.netProfit(), ProfitMath.netProfit(statement.grossProfit(), statement.expenses()));
    }

    @Test
    void closingStockFollowsTheMovementIdentity() {
        BigDecimal closing = ProfitMath.closingStock(
                new BigDecimal("2"),
                new BigDecimal("4"),
                new BigDecimal("1"),
                new BigDecimal("3"),
                new BigDecimal("0.5"),
                new BigDecimal("0.25"));
        assertEquals(new BigDecimal("2.7500"), closing);
    }

    @Test
    void treatsAQuietPeriodAsIdleAndNormalizesNullMoney() {
        ProfitMath.Statement quiet = ProfitMath.statement(null, null, null, null, null);
        assertTrue(ProfitMath.idle(quiet));
        assertEquals(ProfitMath.ZERO, quiet.netProfit());
        assertFalse(ProfitMath.idle(ProfitMath.statement(BigDecimal.ONE, null, null, null, null)));
    }
}
