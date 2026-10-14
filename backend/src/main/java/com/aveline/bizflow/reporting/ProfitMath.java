package com.aveline.bizflow.reporting;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Single profit and stock-flow kernel for the dashboard and every report.
 * Revenue, cost of goods sold, gross profit, and net profit are derived only here.
 */
public final class ProfitMath {

    public static final int SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private ProfitMath() {
    }

    public static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal revenue(BigDecimal grossSales, BigDecimal salesReturns) {
        return money(grossSales).subtract(money(salesReturns));
    }

    public static BigDecimal costOfGoodsSold(BigDecimal issuedCost, BigDecimal restoredCost) {
        return money(issuedCost).subtract(money(restoredCost));
    }

    public static BigDecimal grossProfit(BigDecimal netRevenue, BigDecimal costOfGoodsSold) {
        return money(netRevenue).subtract(money(costOfGoodsSold));
    }

    public static BigDecimal netProfit(BigDecimal grossProfit, BigDecimal expenses) {
        return money(grossProfit).subtract(money(expenses));
    }

    public static BigDecimal closingStock(
            BigDecimal opening,
            BigDecimal purchase,
            BigDecimal purchaseReturn,
            BigDecimal sales,
            BigDecimal salesReturn,
            BigDecimal adjustment) {
        return money(opening)
                .add(money(purchase))
                .subtract(money(purchaseReturn))
                .subtract(money(sales))
                .add(money(salesReturn))
                .add(money(adjustment));
    }

    public static Statement statement(
            BigDecimal grossSales,
            BigDecimal salesReturns,
            BigDecimal issuedCost,
            BigDecimal restoredCost,
            BigDecimal expenses) {
        BigDecimal netRevenue = revenue(grossSales, salesReturns);
        BigDecimal cogs = costOfGoodsSold(issuedCost, restoredCost);
        BigDecimal gross = grossProfit(netRevenue, cogs);
        BigDecimal net = netProfit(gross, expenses);
        return new Statement(
                money(grossSales),
                money(salesReturns),
                netRevenue,
                cogs,
                gross,
                money(expenses),
                net);
    }

    public static boolean idle(Statement statement) {
        return statement.grossSales().signum() == 0
                && statement.salesReturns().signum() == 0
                && statement.costOfGoodsSold().signum() == 0
                && statement.expenses().signum() == 0;
    }

    public record Statement(
            BigDecimal grossSales,
            BigDecimal salesReturns,
            BigDecimal revenue,
            BigDecimal costOfGoodsSold,
            BigDecimal grossProfit,
            BigDecimal expenses,
            BigDecimal netProfit) {
    }
}
