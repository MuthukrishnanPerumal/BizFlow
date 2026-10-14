package com.aveline.bizflow.reporting;

import com.aveline.bizflow.reporting.dto.ReportingDtos.InventoryReport;
import com.aveline.bizflow.reporting.dto.ReportingDtos.InventoryRow;
import com.aveline.bizflow.reporting.dto.ReportingDtos.InventoryTotals;
import com.aveline.bizflow.reporting.dto.ReportingDtos.ProfitReport;
import com.aveline.bizflow.reporting.dto.ReportingDtos.ProfitRow;
import com.aveline.bizflow.reporting.dto.ReportingDtos.PurchaseReport;
import com.aveline.bizflow.reporting.dto.ReportingDtos.PurchaseRow;
import com.aveline.bizflow.reporting.dto.ReportingDtos.PurchaseTotals;
import com.aveline.bizflow.reporting.dto.ReportingDtos.SalesReport;
import com.aveline.bizflow.reporting.dto.ReportingDtos.SalesRow;
import com.aveline.bizflow.reporting.dto.ReportingDtos.SalesTotals;
import com.aveline.bizflow.reporting.dto.ReportingDtos.StatementView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class ReportCsv {

    private ReportCsv() {
    }

    static String sales(SalesReport report) {
        List<String> lines = new ArrayList<>();
        lines.add(row("invoice", "date", "customer", "subtotal", "discount", "tax", "total", "paid", "balance", "status"));
        for (SalesRow item : report.items()) {
            lines.add(row(
                    item.invoice(), item.date(), item.customer(), item.subtotal(), item.discount(),
                    item.tax(), item.total(), item.paid(), item.balance(), item.status()));
        }
        SalesTotals totals = report.totals();
        lines.add(row("", "", "Total", totals.subtotal(), totals.discount(), totals.tax(), totals.total(), totals.paid(), totals.balance(), ""));
        return text(lines);
    }

    static String purchases(PurchaseReport report) {
        List<String> lines = new ArrayList<>();
        lines.add(row("invoice", "date", "supplier", "subtotal", "discount", "tax", "total", "paid", "balance", "status"));
        for (PurchaseRow item : report.items()) {
            lines.add(row(
                    item.invoice(), item.date(), item.supplier(), item.subtotal(), item.discount(),
                    item.tax(), item.total(), item.paid(), item.balance(), item.status()));
        }
        PurchaseTotals totals = report.totals();
        lines.add(row("", "", "Total", totals.subtotal(), totals.discount(), totals.tax(), totals.total(), totals.paid(), totals.balance(), ""));
        return text(lines);
    }

    static String inventory(InventoryReport report) {
        List<String> lines = new ArrayList<>();
        lines.add(row(
                "product", "opening stock", "purchase", "purchase return", "sales", "sales return",
                "adjustment", "closing stock", "stock value"));
        for (InventoryRow item : report.items()) {
            lines.add(row(
                    item.product(), item.openingStock(), item.purchase(), item.purchaseReturn(), item.sales(),
                    item.salesReturn(), item.adjustment(), item.closingStock(), item.stockValue()));
        }
        InventoryTotals totals = report.totals();
        lines.add(row(
                "Total", totals.openingStock(), totals.purchase(), totals.purchaseReturn(), totals.sales(),
                totals.salesReturn(), totals.adjustment(), totals.closingStock(), totals.stockValue()));
        return text(lines);
    }

    static String profit(ProfitReport report) {
        List<String> lines = new ArrayList<>();
        lines.add(row("period", "revenue", "cost of goods sold", "gross profit", "expenses", "net profit"));
        for (ProfitRow item : report.items()) {
            lines.add(row(item.period(), item.revenue(), item.costOfGoodsSold(), item.grossProfit(), item.expenses(), item.netProfit()));
        }
        StatementView totals = report.statement();
        lines.add(row("Total", totals.revenue(), totals.costOfGoodsSold(), totals.grossProfit(), totals.expenses(), totals.netProfit()));
        return text(lines);
    }

    private static String text(List<String> lines) {
        return "\uFEFF" + String.join("\n", lines) + "\n";
    }

    private static String row(Object... cells) {
        List<String> encoded = new ArrayList<>();
        for (Object cell : cells) {
            encoded.add(escape(cell));
        }
        return String.join(",", encoded);
    }

    private static String escape(Object value) {
        String text;
        if (value == null) {
            text = "";
        } else if (value instanceof BigDecimal amount) {
            text = amount.toPlainString();
        } else {
            text = String.valueOf(value);
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
