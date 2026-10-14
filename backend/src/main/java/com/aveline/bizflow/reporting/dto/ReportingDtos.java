package com.aveline.bizflow.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ReportingDtos {

    private ReportingDtos() {
    }

    public record PeriodView(String preset, LocalDate from, LocalDate to, String grain) {
    }

    public record StatementView(
            BigDecimal grossSales,
            BigDecimal salesReturns,
            BigDecimal revenue,
            BigDecimal costOfGoodsSold,
            BigDecimal grossProfit,
            BigDecimal expenses,
            BigDecimal netProfit) {
    }

    public record KpiView(
            BigDecimal totalSales,
            BigDecimal totalPurchases,
            BigDecimal grossProfit,
            BigDecimal netProfit,
            BigDecimal totalStockValue,
            long lowStockItems,
            long outOfStockItems,
            BigDecimal receivables,
            BigDecimal payables,
            BigDecimal expenses) {
    }

    public record AmountPoint(String key, String label, BigDecimal amount) {
    }

    public record ProductPoint(String key, String label, BigDecimal amount, BigDecimal quantity) {
    }

    public record ProfitPoint(
            String key,
            String label,
            BigDecimal revenue,
            BigDecimal costOfGoodsSold,
            BigDecimal grossProfit,
            BigDecimal netProfit) {
    }

    public record ChartView(
            List<AmountPoint> salesTrend,
            List<AmountPoint> purchaseTrend,
            List<ProfitPoint> profitTrend,
            List<AmountPoint> salesByCategory,
            List<ProductPoint> topProducts,
            List<AmountPoint> purchasesBySupplier,
            List<AmountPoint> stockValueByCategory,
            List<AmountPoint> paymentBreakdown) {
    }

    public record RecentDocument(long id, String number, LocalDate date, String party, BigDecimal amount, String status) {
    }

    public record StockAlert(long id, String product, String category, BigDecimal onHand, BigDecimal reorderLevel) {
    }

    public record PartyRank(long id, String name, long documents, BigDecimal amount) {
    }

    public record RecentPayment(
            long id,
            String number,
            LocalDate date,
            String party,
            String direction,
            String method,
            BigDecimal amount,
            String status) {
    }

    public record StockMovement(
            long id,
            LocalDate date,
            String product,
            String type,
            String reference,
            BigDecimal quantityIn,
            BigDecimal quantityOut,
            BigDecimal amount) {
    }

    public record WidgetView(
            List<RecentDocument> recentSales,
            List<RecentDocument> recentPurchases,
            List<StockAlert> lowStock,
            List<StockAlert> outOfStock,
            List<PartyRank> topCustomers,
            List<PartyRank> topSuppliers,
            List<RecentPayment> recentPayments,
            List<StockMovement> recentMovements) {
    }

    public record DashboardResponse(
            PeriodView period,
            KpiView kpis,
            StatementView statement,
            ChartView charts,
            WidgetView widgets) {
    }

    public record SalesRow(
            long id,
            String invoice,
            LocalDate date,
            String customer,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal tax,
            BigDecimal total,
            BigDecimal paid,
            BigDecimal balance,
            String status) {
    }

    public record SalesTotals(
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal tax,
            BigDecimal total,
            BigDecimal paid,
            BigDecimal balance) {
    }

    public record SalesReport(
            PeriodView period,
            List<SalesRow> items,
            SalesTotals totals,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record PurchaseRow(
            long id,
            String invoice,
            LocalDate date,
            String supplier,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal tax,
            BigDecimal total,
            BigDecimal paid,
            BigDecimal balance,
            String status) {
    }

    public record PurchaseTotals(
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal tax,
            BigDecimal total,
            BigDecimal paid,
            BigDecimal balance) {
    }

    public record PurchaseReport(
            PeriodView period,
            List<PurchaseRow> items,
            PurchaseTotals totals,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record InventoryRow(
            long id,
            String product,
            BigDecimal openingStock,
            BigDecimal purchase,
            BigDecimal purchaseReturn,
            BigDecimal sales,
            BigDecimal salesReturn,
            BigDecimal adjustment,
            BigDecimal closingStock,
            BigDecimal stockValue) {
    }

    public record InventoryTotals(
            BigDecimal openingStock,
            BigDecimal purchase,
            BigDecimal purchaseReturn,
            BigDecimal sales,
            BigDecimal salesReturn,
            BigDecimal adjustment,
            BigDecimal closingStock,
            BigDecimal stockValue) {
    }

    public record InventoryReport(
            PeriodView period,
            List<InventoryRow> items,
            InventoryTotals totals,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record ProfitRow(
            String period,
            BigDecimal revenue,
            BigDecimal costOfGoodsSold,
            BigDecimal grossProfit,
            BigDecimal expenses,
            BigDecimal netProfit) {
    }

    public record ProfitReport(
            PeriodView period,
            StatementView statement,
            List<ProfitRow> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
