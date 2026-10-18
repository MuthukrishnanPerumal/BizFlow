package com.aveline.bizflow.reporting;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.reporting.dto.ReportingDtos.AmountPoint;
import com.aveline.bizflow.reporting.dto.ReportingDtos.InventoryRow;
import com.aveline.bizflow.reporting.dto.ReportingDtos.InventoryTotals;
import com.aveline.bizflow.reporting.dto.ReportingDtos.PartyRank;
import com.aveline.bizflow.reporting.dto.ReportingDtos.ProductPoint;
import com.aveline.bizflow.reporting.dto.ReportingDtos.PurchaseRow;
import com.aveline.bizflow.reporting.dto.ReportingDtos.PurchaseTotals;
import com.aveline.bizflow.reporting.dto.ReportingDtos.RecentDocument;
import com.aveline.bizflow.reporting.dto.ReportingDtos.RecentPayment;
import com.aveline.bizflow.reporting.dto.ReportingDtos.SalesRow;
import com.aveline.bizflow.reporting.dto.ReportingDtos.SalesTotals;
import com.aveline.bizflow.reporting.dto.ReportingDtos.StockAlert;
import com.aveline.bizflow.reporting.dto.ReportingDtos.StockMovement;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ReportingReadModel {

    static final String POSTED = "'CONFIRMED', 'COMPLETED', 'PARTIALLY_RETURNED', 'RETURNED'";

    private static final String LIKE = "LIKE CONCAT('%', :search, '%') ESCAPE '\\\\'";

    private static final Map<String, String> SALES_SORT = Map.of(
            "invoice", "s.sale_number",
            "date", "s.invoice_date",
            "customer", "c.customer_name",
            "subtotal", "s.subtotal",
            "discount", "s.total_discount",
            "tax", "s.tax_amount",
            "total", "s.grand_total",
            "paid", "s.paid_amount",
            "balance", "s.balance_amount",
            "status", "s.status");

    private static final Map<String, String> PURCHASE_SORT = Map.of(
            "invoice", "p.invoice_number",
            "date", "p.purchase_date",
            "supplier", "s.supplier_name",
            "subtotal", "p.subtotal",
            "discount", "p.total_discount",
            "tax", "p.tax_amount",
            "total", "p.grand_total",
            "paid", "p.paid_amount",
            "balance", "p.balance_amount",
            "status", "p.status");

    private static final Map<String, String> INVENTORY_SORT = Map.of(
            "product", "product",
            "openingStock", "opening_stock",
            "purchase", "purchase_qty",
            "purchaseReturn", "purchase_return_qty",
            "sales", "sales_qty",
            "salesReturn", "sales_return_qty",
            "adjustment", "adjustment_qty",
            "closingStock", "closing_stock",
            "stockValue", "stock_value");

    private final NamedParameterJdbcTemplate jdbc;

    public ReportingReadModel(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public FlowTotals flow(LocalDate from, LocalDate to) {
        String sql = """
                SELECT
                  (SELECT COALESCE(SUM(grand_total), 0) FROM sales
                    WHERE is_deleted = 0 AND status IN (%s)
                      AND invoice_date BETWEEN :from AND :to) AS gross_sales,
                  (SELECT COALESCE(SUM(grand_total), 0) FROM sales_returns
                    WHERE is_deleted = 0 AND return_date BETWEEN :from AND :to) AS sales_returns,
                  (SELECT COALESCE(SUM(grand_total), 0) FROM purchases
                    WHERE is_deleted = 0 AND status IN (%s)
                      AND purchase_date BETWEEN :from AND :to) AS gross_purchases,
                  (SELECT COALESCE(SUM(CASE WHEN transaction_type = 'SALE' THEN total_value ELSE 0 END), 0)
                    FROM stock_ledger
                    WHERE is_deleted = 0 AND entry_date BETWEEN :from AND :to) AS issued_cost,
                  (SELECT COALESCE(SUM(CASE WHEN transaction_type IN ('SALES_RETURN', 'SALE_REVERSAL') THEN total_value ELSE 0 END), 0)
                    FROM stock_ledger
                    WHERE is_deleted = 0 AND entry_date BETWEEN :from AND :to) AS restored_cost,
                  (SELECT COALESCE(SUM(amount), 0) FROM expenses
                    WHERE is_deleted = 0 AND status = 'POSTED'
                      AND expense_date BETWEEN :from AND :to) AS expenses,
                  (SELECT COALESCE(SUM(balance_amount), 0) FROM sales
                    WHERE is_deleted = 0 AND status IN (%s)
                      AND invoice_date BETWEEN :from AND :to) AS receivables,
                  (SELECT COALESCE(SUM(balance_amount), 0) FROM purchases
                    WHERE is_deleted = 0 AND status IN (%s)
                      AND purchase_date BETWEEN :from AND :to) AS payables
                """.formatted(POSTED, POSTED, POSTED, POSTED);
        return jdbc.queryForObject(sql, dates(from, to), (rs, row) -> new FlowTotals(
                money(rs, "gross_sales"),
                money(rs, "sales_returns"),
                money(rs, "gross_purchases"),
                money(rs, "issued_cost"),
                money(rs, "restored_cost"),
                money(rs, "expenses"),
                money(rs, "receivables"),
                money(rs, "payables")));
    }

    public StockSnapshot stock(LocalDate to, boolean applyReserved) {
        return stockBoard(to, applyReserved, 0, 0).snapshot();
    }

    /**
     * Loads stock positions once and derives KPI snapshot, category chart, and alerts.
     * Replaces up to four separate full {@code stock_ledger} rollups per dashboard request.
     */
    public StockBoard stockBoard(LocalDate to, boolean applyReserved, int categoryLimit, int alertLimit) {
        String sql = """
                SELECT product_id, product_code, product_name, category_id, category_name,
                       reorder_level, available, stock_value
                FROM (%s) stock_position
                """.formatted(stockSql());
        List<StockPositionRow> rows = jdbc.query(sql, stockParams(to, applyReserved), (rs, row) -> new StockPositionRow(
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getString("category_name"),
                money(rs, "available"),
                money(rs, "reorder_level"),
                money(rs, "stock_value")));

        BigDecimal stockValue = ProfitMath.ZERO;
        long lowStock = 0;
        long outOfStock = 0;
        Map<String, BigDecimal> categoryTotals = new HashMap<>();
        List<StockAlert> lowCandidates = new ArrayList<>();
        List<StockAlert> outCandidates = new ArrayList<>();

        for (StockPositionRow position : rows) {
            stockValue = stockValue.add(position.stockValue());
            boolean out = position.available().compareTo(ProfitMath.ZERO) <= 0;
            boolean low = !out
                    && position.reorderLevel().compareTo(ProfitMath.ZERO) > 0
                    && position.available().compareTo(position.reorderLevel()) <= 0;
            if (out) {
                outOfStock++;
                if (alertLimit > 0) {
                    outCandidates.add(new StockAlert(
                            position.productId(),
                            position.productName(),
                            position.categoryName(),
                            position.available(),
                            position.reorderLevel()));
                }
            } else if (low) {
                lowStock++;
                if (alertLimit > 0) {
                    lowCandidates.add(new StockAlert(
                            position.productId(),
                            position.productName(),
                            position.categoryName(),
                            position.available(),
                            position.reorderLevel()));
                }
            }
            if (categoryLimit > 0 && position.stockValue().signum() != 0) {
                categoryTotals.merge(position.categoryName(), position.stockValue(), BigDecimal::add);
            }
        }

        List<AmountPoint> byCategory = categoryLimit <= 0
                ? List.of()
                : categoryTotals.entrySet().stream()
                        .sorted((left, right) -> right.getValue().compareTo(left.getValue()))
                        .limit(categoryLimit)
                        .map(entry -> new AmountPoint(entry.getKey(), entry.getKey(), entry.getValue()))
                        .toList();

        List<StockAlert> lowAlerts = alertLimit <= 0
                ? List.of()
                : lowCandidates.stream()
                        .sorted(Comparator.comparing(StockAlert::onHand).thenComparing(StockAlert::product))
                        .limit(alertLimit)
                        .toList();
        List<StockAlert> outAlerts = alertLimit <= 0
                ? List.of()
                : outCandidates.stream()
                        .sorted(Comparator.comparing(StockAlert::product))
                        .limit(alertLimit)
                        .toList();

        return new StockBoard(
                new StockSnapshot(stockValue, lowStock, outOfStock),
                byCategory,
                lowAlerts,
                outAlerts);
    }

    public long countDraftSales(LocalDate from, LocalDate to) {
        String sql = """
                SELECT COUNT(*) FROM sales
                WHERE is_deleted = 0 AND status = 'DRAFT'
                  AND invoice_date BETWEEN :from AND :to
                """;
        Long count = jdbc.queryForObject(sql, dates(from, to), Long.class);
        return count == null ? 0L : count;
    }

    public long countDraftPurchases(LocalDate from, LocalDate to) {
        String sql = """
                SELECT COUNT(*) FROM purchases
                WHERE is_deleted = 0 AND status = 'DRAFT'
                  AND purchase_date BETWEEN :from AND :to
                """;
        Long count = jdbc.queryForObject(sql, dates(from, to), Long.class);
        return count == null ? 0L : count;
    }

    public Map<String, BigDecimal> salesByBucket(LocalDate from, LocalDate to, ReportRanges.Grain grain) {
        String bucket = dateBucket("invoice_date", grain);
        String sql = """
                SELECT %s AS bucket, COALESCE(SUM(grand_total), 0) AS amount
                FROM sales
                WHERE is_deleted = 0 AND status IN (%s)
                  AND invoice_date BETWEEN :from AND :to
                GROUP BY %s
                """.formatted(bucket, POSTED, bucket);
        return amounts(sql, dates(from, to));
    }

    public Map<String, BigDecimal> purchaseByBucket(LocalDate from, LocalDate to, ReportRanges.Grain grain) {
        String bucket = dateBucket("purchase_date", grain);
        String sql = """
                SELECT %s AS bucket, COALESCE(SUM(grand_total), 0) AS amount
                FROM purchases
                WHERE is_deleted = 0 AND status IN (%s)
                  AND purchase_date BETWEEN :from AND :to
                GROUP BY %s
                """.formatted(bucket, POSTED, bucket);
        return amounts(sql, dates(from, to));
    }

    public Map<String, BigDecimal> salesReturnsByBucket(LocalDate from, LocalDate to, ReportRanges.Grain grain) {
        String bucket = dateBucket("return_date", grain);
        String sql = """
                SELECT %s AS bucket, COALESCE(SUM(grand_total), 0) AS amount
                FROM sales_returns
                WHERE is_deleted = 0 AND return_date BETWEEN :from AND :to
                GROUP BY %s
                """.formatted(bucket, bucket);
        return amounts(sql, dates(from, to));
    }

    public Map<String, CostParts> cogsByBucket(LocalDate from, LocalDate to, ReportRanges.Grain grain) {
        String bucket = dateBucket("entry_date", grain);
        String sql = """
                SELECT %s AS bucket,
                  COALESCE(SUM(CASE WHEN transaction_type = 'SALE' THEN total_value ELSE 0 END), 0) AS issued_cost,
                  COALESCE(SUM(CASE WHEN transaction_type IN ('SALES_RETURN', 'SALE_REVERSAL') THEN total_value ELSE 0 END), 0) AS restored_cost
                FROM stock_ledger
                WHERE is_deleted = 0 AND entry_date BETWEEN :from AND :to
                GROUP BY %s
                """.formatted(bucket, bucket);
        Map<String, CostParts> values = new HashMap<>();
        jdbc.query(sql, dates(from, to), rs -> {
            values.put(rs.getString("bucket"), new CostParts(money(rs, "issued_cost"), money(rs, "restored_cost")));
        });
        return values;
    }

    public Map<String, BigDecimal> expensesByBucket(LocalDate from, LocalDate to, ReportRanges.Grain grain) {
        String bucket = dateBucket("expense_date", grain);
        String sql = """
                SELECT %s AS bucket, COALESCE(SUM(amount), 0) AS amount
                FROM expenses
                WHERE is_deleted = 0 AND status = 'POSTED'
                  AND expense_date BETWEEN :from AND :to
                GROUP BY %s
                """.formatted(bucket, bucket);
        return amounts(sql, dates(from, to));
    }

    public List<AmountPoint> salesByCategory(LocalDate from, LocalDate to, int limit) {
        Map<String, BigDecimal> net = new HashMap<>();
        String sales = """
                SELECT c.name AS label, COALESCE(SUM(si.line_total), 0) AS amount
                FROM sale_item si
                JOIN sales s ON s.id = si.sale_id AND s.is_deleted = 0
                JOIN products p ON p.id = si.product_id
                JOIN categories c ON c.id = p.category_id
                WHERE si.is_deleted = 0
                  AND s.status IN (%s)
                  AND s.invoice_date BETWEEN :from AND :to
                GROUP BY c.id, c.name
                """.formatted(POSTED);
        String returns = """
                SELECT c.name AS label, COALESCE(SUM(ri.line_total), 0) AS amount
                FROM sales_return_item ri
                JOIN sales_returns r ON r.id = ri.sales_return_id AND r.is_deleted = 0
                JOIN sale_item si ON si.id = ri.sale_item_id
                JOIN products p ON p.id = si.product_id
                JOIN categories c ON c.id = p.category_id
                WHERE ri.is_deleted = 0 AND r.return_date BETWEEN :from AND :to
                GROUP BY c.id, c.name
                """;
        addAmounts(net, sales, dates(from, to), true);
        addAmounts(net, returns, dates(from, to), false);
        return topAmounts(net, limit);
    }

    public List<ProductPoint> topProducts(LocalDate from, LocalDate to, int limit) {
        Map<String, ProductPoint> net = new LinkedHashMap<>();
        String sales = """
                SELECT p.name AS label,
                  COALESCE(SUM(si.line_total), 0) AS amount,
                  COALESCE(SUM(si.quantity), 0) AS quantity
                FROM sale_item si
                JOIN sales s ON s.id = si.sale_id AND s.is_deleted = 0
                JOIN products p ON p.id = si.product_id
                WHERE si.is_deleted = 0
                  AND s.status IN (%s)
                  AND s.invoice_date BETWEEN :from AND :to
                GROUP BY p.id, p.name
                """.formatted(POSTED);
        String returns = """
                SELECT p.name AS label,
                  COALESCE(SUM(ri.line_total), 0) AS amount,
                  COALESCE(SUM(ri.quantity), 0) AS quantity
                FROM sales_return_item ri
                JOIN sales_returns r ON r.id = ri.sales_return_id AND r.is_deleted = 0
                JOIN sale_item si ON si.id = ri.sale_item_id
                JOIN products p ON p.id = si.product_id
                WHERE ri.is_deleted = 0 AND r.return_date BETWEEN :from AND :to
                GROUP BY p.id, p.name
                """;
        jdbc.query(sales, dates(from, to), (ResultSet rs) -> {
            accumulateProduct(net, rs, true);
        });
        jdbc.query(returns, dates(from, to), (ResultSet rs) -> {
            accumulateProduct(net, rs, false);
        });
        return net.values().stream()
                .filter(point -> point.amount().signum() != 0 || point.quantity().signum() != 0)
                .sorted((left, right) -> right.amount().compareTo(left.amount()))
                .limit(limit)
                .map(point -> new ProductPoint(point.label(), point.label(), point.amount(), point.quantity()))
                .toList();
    }

    public List<AmountPoint> purchasesBySupplier(LocalDate from, LocalDate to, int limit) {
        Map<String, BigDecimal> net = new HashMap<>();
        String purchases = """
                SELECT s.supplier_name AS label, COALESCE(SUM(p.grand_total), 0) AS amount
                FROM purchases p
                JOIN suppliers s ON s.id = p.supplier_id
                WHERE p.is_deleted = 0 AND p.status IN (%s)
                  AND p.purchase_date BETWEEN :from AND :to
                GROUP BY s.id, s.supplier_name
                """.formatted(POSTED);
        String returns = """
                SELECT s.supplier_name AS label, COALESCE(SUM(r.grand_total), 0) AS amount
                FROM purchase_returns r
                JOIN suppliers s ON s.id = r.supplier_id
                WHERE r.is_deleted = 0 AND r.return_date BETWEEN :from AND :to
                GROUP BY s.id, s.supplier_name
                """;
        addAmounts(net, purchases, dates(from, to), true);
        addAmounts(net, returns, dates(from, to), false);
        return topAmounts(net, limit);
    }

    public List<AmountPoint> stockByCategory(LocalDate to, boolean applyReserved, int limit) {
        String sql = """
                SELECT category_name AS label, COALESCE(SUM(stock_value), 0) AS amount
                FROM (%s) stock_position
                GROUP BY category_id, category_name
                HAVING amount <> 0
                ORDER BY amount DESC
                LIMIT %d
                """.formatted(stockSql(), limit);
        return jdbc.query(sql, stockParams(to, applyReserved), (rs, row) -> new AmountPoint(
                rs.getString("label"),
                rs.getString("label"),
                money(rs, "amount")));
    }

    public List<AmountPoint> paymentsByMethod(LocalDate from, LocalDate to) {
        String sql = """
                SELECT payment_method AS label, COALESCE(SUM(amount), 0) AS amount
                FROM payments
                WHERE is_deleted = 0 AND status = 'POSTED'
                  AND payment_date BETWEEN :from AND :to
                GROUP BY payment_method
                ORDER BY amount DESC
                """;
        return jdbc.query(sql, dates(from, to), (rs, row) -> new AmountPoint(
                rs.getString("label"),
                rs.getString("label"),
                money(rs, "amount")));
    }

    public List<RecentDocument> recentSales(LocalDate from, LocalDate to, int limit) {
        String sql = """
                SELECT s.id, s.sale_number AS number, s.invoice_date AS dated, c.customer_name AS party,
                       s.grand_total AS amount, s.status
                FROM sales s
                JOIN customers c ON c.id = s.customer_id
                WHERE s.is_deleted = 0 AND s.status IN (%s)
                  AND s.invoice_date BETWEEN :from AND :to
                ORDER BY s.invoice_date DESC, s.id DESC
                LIMIT %d
                """.formatted(POSTED, limit);
        return documents(sql, dates(from, to));
    }

    public List<RecentDocument> recentPurchases(LocalDate from, LocalDate to, int limit) {
        String sql = """
                SELECT p.id, p.purchase_number AS number, p.purchase_date AS dated, s.supplier_name AS party,
                       p.grand_total AS amount, p.status
                FROM purchases p
                JOIN suppliers s ON s.id = p.supplier_id
                WHERE p.is_deleted = 0 AND p.status IN (%s)
                  AND p.purchase_date BETWEEN :from AND :to
                ORDER BY p.purchase_date DESC, p.id DESC
                LIMIT %d
                """.formatted(POSTED, limit);
        return documents(sql, dates(from, to));
    }

    public List<StockAlert> stockAlerts(LocalDate to, boolean applyReserved, boolean outOfStock, int limit) {
        String predicate = outOfStock
                ? "available <= 0"
                : "available > 0 AND reorder_level > 0 AND available <= reorder_level";
        String order = outOfStock ? "product_name ASC" : "available ASC, product_name ASC";
        String sql = """
                SELECT product_id, product_name, product_code, category_name, available, reorder_level
                FROM (%s) stock_position
                WHERE %s
                ORDER BY %s
                LIMIT %d
                """.formatted(stockSql(), predicate, order, limit);
        return jdbc.query(sql, stockParams(to, applyReserved), (rs, row) -> new StockAlert(
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getString("category_name"),
                money(rs, "available"),
                money(rs, "reorder_level")));
    }

    public List<PartyRank> topCustomers(LocalDate from, LocalDate to, int limit) {
        String sql = """
                SELECT c.id, c.customer_name AS name, COUNT(*) AS documents, COALESCE(SUM(s.grand_total), 0) AS amount
                FROM sales s
                JOIN customers c ON c.id = s.customer_id
                WHERE s.is_deleted = 0 AND s.status IN (%s)
                  AND s.invoice_date BETWEEN :from AND :to
                GROUP BY c.id, c.customer_name
                ORDER BY amount DESC, c.customer_name ASC
                LIMIT %d
                """.formatted(POSTED, limit);
        return ranks(sql, dates(from, to));
    }

    public List<PartyRank> topSuppliers(LocalDate from, LocalDate to, int limit) {
        String sql = """
                SELECT s.id, s.supplier_name AS name, COUNT(*) AS documents, COALESCE(SUM(p.grand_total), 0) AS amount
                FROM purchases p
                JOIN suppliers s ON s.id = p.supplier_id
                WHERE p.is_deleted = 0 AND p.status IN (%s)
                  AND p.purchase_date BETWEEN :from AND :to
                GROUP BY s.id, s.supplier_name
                ORDER BY amount DESC, s.supplier_name ASC
                LIMIT %d
                """.formatted(POSTED, limit);
        return ranks(sql, dates(from, to));
    }

    public List<RecentPayment> recentPayments(LocalDate from, LocalDate to, int limit) {
        String sql = """
                SELECT p.id, p.payment_number, p.payment_date, p.direction, p.payment_method, p.amount, p.status,
                       COALESCE(c.customer_name, s.supplier_name, '') AS party
                FROM payments p
                LEFT JOIN customers c ON c.id = p.customer_id
                LEFT JOIN suppliers s ON s.id = p.supplier_id
                WHERE p.is_deleted = 0 AND p.status = 'POSTED'
                  AND p.payment_date BETWEEN :from AND :to
                ORDER BY p.payment_date DESC, p.id DESC
                LIMIT %d
                """.formatted(limit);
        return jdbc.query(sql, dates(from, to), (rs, row) -> new RecentPayment(
                rs.getLong("id"),
                rs.getString("payment_number"),
                rs.getDate("payment_date").toLocalDate(),
                rs.getString("party"),
                rs.getString("direction"),
                rs.getString("payment_method"),
                money(rs, "amount"),
                rs.getString("status")));
    }

    public List<StockMovement> recentMovements(LocalDate from, LocalDate to, int limit) {
        String sql = """
                SELECT l.id, l.entry_date, p.name AS product, l.transaction_type, l.reference_number,
                       l.quantity_in, l.quantity_out, l.total_value
                FROM stock_ledger l
                JOIN products p ON p.id = l.product_id
                WHERE l.is_deleted = 0 AND l.entry_date BETWEEN :from AND :to
                ORDER BY l.entry_date DESC, l.id DESC
                LIMIT %d
                """.formatted(limit);
        return jdbc.query(sql, dates(from, to), (rs, row) -> new StockMovement(
                rs.getLong("id"),
                rs.getDate("entry_date").toLocalDate(),
                rs.getString("product"),
                rs.getString("transaction_type"),
                rs.getString("reference_number"),
                money(rs, "quantity_in"),
                money(rs, "quantity_out"),
                money(rs, "total_value")));
    }

    public long countSales(DocumentQuery query) {
        String sql = "SELECT COUNT(*) " + salesFrom(query);
        Long count = jdbc.queryForObject(sql, documentParams(query), Long.class);
        return count == null ? 0 : count;
    }

    public SalesTotals salesTotals(DocumentQuery query) {
        String sql = """
                SELECT
                  COALESCE(SUM(s.subtotal), 0) AS subtotal,
                  COALESCE(SUM(s.total_discount), 0) AS discount,
                  COALESCE(SUM(s.tax_amount), 0) AS tax,
                  COALESCE(SUM(s.grand_total), 0) AS total,
                  COALESCE(SUM(s.paid_amount), 0) AS paid,
                  COALESCE(SUM(s.balance_amount), 0) AS balance
                %s
                """.formatted(salesFrom(query));
        return jdbc.queryForObject(sql, documentParams(query), (rs, row) -> new SalesTotals(
                money(rs, "subtotal"),
                money(rs, "discount"),
                money(rs, "tax"),
                money(rs, "total"),
                money(rs, "paid"),
                money(rs, "balance")));
    }

    public List<SalesRow> salesPage(DocumentQuery query, String sort, int limit, int offset) {
        String sql = """
                SELECT s.id, s.sale_number, s.invoice_date, c.customer_name, s.subtotal, s.total_discount,
                       s.tax_amount, s.grand_total, s.paid_amount, s.balance_amount, s.status
                %s
                ORDER BY %s
                LIMIT %d OFFSET %d
                """.formatted(salesFrom(query), order(SALES_SORT, sort, "s.invoice_date", "s.id"), limit, offset);
        return jdbc.query(sql, documentParams(query), (rs, row) -> new SalesRow(
                rs.getLong("id"),
                rs.getString("sale_number"),
                rs.getDate("invoice_date").toLocalDate(),
                rs.getString("customer_name"),
                money(rs, "subtotal"),
                money(rs, "total_discount"),
                money(rs, "tax_amount"),
                money(rs, "grand_total"),
                money(rs, "paid_amount"),
                money(rs, "balance_amount"),
                rs.getString("status")));
    }

    public long countPurchases(DocumentQuery query) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) " + purchaseFrom(query), documentParams(query), Long.class);
        return count == null ? 0 : count;
    }

    public PurchaseTotals purchaseTotals(DocumentQuery query) {
        String sql = """
                SELECT
                  COALESCE(SUM(p.subtotal), 0) AS subtotal,
                  COALESCE(SUM(p.total_discount), 0) AS discount,
                  COALESCE(SUM(p.tax_amount), 0) AS tax,
                  COALESCE(SUM(p.grand_total), 0) AS total,
                  COALESCE(SUM(p.paid_amount), 0) AS paid,
                  COALESCE(SUM(p.balance_amount), 0) AS balance
                %s
                """.formatted(purchaseFrom(query));
        return jdbc.queryForObject(sql, documentParams(query), (rs, row) -> new PurchaseTotals(
                money(rs, "subtotal"),
                money(rs, "discount"),
                money(rs, "tax"),
                money(rs, "total"),
                money(rs, "paid"),
                money(rs, "balance")));
    }

    public List<PurchaseRow> purchasePage(DocumentQuery query, String sort, int limit, int offset) {
        String sql = """
                SELECT p.id, p.invoice_number, p.purchase_date, s.supplier_name, p.subtotal, p.total_discount,
                       p.tax_amount, p.grand_total, p.paid_amount, p.balance_amount, p.status
                %s
                ORDER BY %s
                LIMIT %d OFFSET %d
                """.formatted(purchaseFrom(query), order(PURCHASE_SORT, sort, "p.purchase_date", "p.id"), limit, offset);
        return jdbc.query(sql, documentParams(query), (rs, row) -> new PurchaseRow(
                rs.getLong("id"),
                rs.getString("invoice_number"),
                rs.getDate("purchase_date").toLocalDate(),
                rs.getString("supplier_name"),
                money(rs, "subtotal"),
                money(rs, "total_discount"),
                money(rs, "tax_amount"),
                money(rs, "grand_total"),
                money(rs, "paid_amount"),
                money(rs, "balance_amount"),
                rs.getString("status")));
    }

    public InventoryPage inventory(DocumentQuery query, String sort, int limit, int offset) {
        String grouped = inventoryGrouped();
        // Window aggregates avoid a second full product×ledger scan for totals.
        String pageSql = """
                SELECT product_id, product, opening_stock, purchase_qty, purchase_return_qty,
                       sales_qty, sales_return_qty, adjustment_qty,
                       (opening_stock + purchase_qty - purchase_return_qty - sales_qty + sales_return_qty + adjustment_qty) AS closing_stock,
                       stock_value,
                       COUNT(*) OVER() AS row_count,
                       COALESCE(SUM(opening_stock) OVER(), 0) AS total_opening_stock,
                       COALESCE(SUM(purchase_qty) OVER(), 0) AS total_purchase_qty,
                       COALESCE(SUM(purchase_return_qty) OVER(), 0) AS total_purchase_return_qty,
                       COALESCE(SUM(sales_qty) OVER(), 0) AS total_sales_qty,
                       COALESCE(SUM(sales_return_qty) OVER(), 0) AS total_sales_return_qty,
                       COALESCE(SUM(adjustment_qty) OVER(), 0) AS total_adjustment_qty,
                       COALESCE(SUM(stock_value) OVER(), 0) AS total_stock_value
                FROM (%s) inventory_report
                ORDER BY %s
                LIMIT %d OFFSET %d
                """.formatted(grouped, order(INVENTORY_SORT, sort, "product", "product_id"), limit, offset);
        final long[] count = {0};
        final InventoryTotals[] totalsHolder = {null};
        List<InventoryRow> rows = jdbc.query(pageSql, documentParams(query), (rs, row) -> {
            if (totalsHolder[0] == null) {
                count[0] = rs.getLong("row_count");
                BigDecimal opening = money(rs, "total_opening_stock");
                BigDecimal purchase = money(rs, "total_purchase_qty");
                BigDecimal purchaseReturn = money(rs, "total_purchase_return_qty");
                BigDecimal sales = money(rs, "total_sales_qty");
                BigDecimal salesReturn = money(rs, "total_sales_return_qty");
                BigDecimal adjustment = money(rs, "total_adjustment_qty");
                totalsHolder[0] = new InventoryTotals(
                        opening,
                        purchase,
                        purchaseReturn,
                        sales,
                        salesReturn,
                        adjustment,
                        ProfitMath.closingStock(opening, purchase, purchaseReturn, sales, salesReturn, adjustment),
                        money(rs, "total_stock_value"));
            }
            return new InventoryRow(
                    rs.getLong("product_id"),
                    rs.getString("product"),
                    money(rs, "opening_stock"),
                    money(rs, "purchase_qty"),
                    money(rs, "purchase_return_qty"),
                    money(rs, "sales_qty"),
                    money(rs, "sales_return_qty"),
                    money(rs, "adjustment_qty"),
                    money(rs, "closing_stock"),
                    money(rs, "stock_value"));
        });
        if (!rows.isEmpty()) {
            return new InventoryPage(rows, totalsHolder[0] == null ? emptyInventory() : totalsHolder[0], count[0]);
        }
        if (offset <= 0) {
            return new InventoryPage(rows, emptyInventory(), 0);
        }
        // High offset with no page rows: still need accurate totals/count.
        String totalsSql = """
                SELECT
                  COUNT(*) AS row_count,
                  COALESCE(SUM(opening_stock), 0) AS opening_stock,
                  COALESCE(SUM(purchase_qty), 0) AS purchase_qty,
                  COALESCE(SUM(purchase_return_qty), 0) AS purchase_return_qty,
                  COALESCE(SUM(sales_qty), 0) AS sales_qty,
                  COALESCE(SUM(sales_return_qty), 0) AS sales_return_qty,
                  COALESCE(SUM(adjustment_qty), 0) AS adjustment_qty,
                  COALESCE(SUM(stock_value), 0) AS stock_value
                FROM (%s) inventory_report
                """.formatted(grouped);
        final long[] fallbackCount = {0};
        InventoryTotals totals = jdbc.queryForObject(totalsSql, documentParams(query), (rs, row) -> {
            fallbackCount[0] = rs.getLong("row_count");
            BigDecimal opening = money(rs, "opening_stock");
            BigDecimal purchase = money(rs, "purchase_qty");
            BigDecimal purchaseReturn = money(rs, "purchase_return_qty");
            BigDecimal sales = money(rs, "sales_qty");
            BigDecimal salesReturn = money(rs, "sales_return_qty");
            BigDecimal adjustment = money(rs, "adjustment_qty");
            return new InventoryTotals(
                    opening,
                    purchase,
                    purchaseReturn,
                    sales,
                    salesReturn,
                    adjustment,
                    ProfitMath.closingStock(opening, purchase, purchaseReturn, sales, salesReturn, adjustment),
                    money(rs, "stock_value"));
        });
        return new InventoryPage(rows, totals == null ? emptyInventory() : totals, fallbackCount[0]);
    }

    private String salesFrom(DocumentQuery query) {
        return """
                FROM sales s
                JOIN customers c ON c.id = s.customer_id
                WHERE s.is_deleted = 0
                  AND s.invoice_date BETWEEN :from AND :to
                  AND (:postedOnly = 0 OR s.status IN (%s))
                  AND (:status IS NULL OR s.status = :status)
                  AND (
                    :search IS NULL
                    OR s.sale_number %s
                    OR c.customer_name %s
                    OR IFNULL(c.phone, '') %s
                  )
                """.formatted(POSTED, LIKE, LIKE, LIKE);
    }

    private String purchaseFrom(DocumentQuery query) {
        return """
                FROM purchases p
                JOIN suppliers s ON s.id = p.supplier_id
                WHERE p.is_deleted = 0
                  AND p.purchase_date BETWEEN :from AND :to
                  AND (:postedOnly = 0 OR p.status IN (%s))
                  AND (:status IS NULL OR p.status = :status)
                  AND (
                    :search IS NULL
                    OR p.purchase_number %s
                    OR p.invoice_number %s
                    OR s.supplier_name %s
                    OR IFNULL(s.phone, '') %s
                  )
                """.formatted(POSTED, LIKE, LIKE, LIKE, LIKE);
    }

    private String inventoryGrouped() {
        return """
                SELECT
                  p.id AS product_id,
                  CONCAT(p.name, ' · ', p.product_code) AS product,
                  COALESCE(SUM(CASE WHEN l.entry_date < :from THEN l.quantity_in - l.quantity_out ELSE 0 END), 0) AS opening_stock,
                  COALESCE(SUM(CASE
                    WHEN l.entry_date BETWEEN :from AND :to AND l.transaction_type = 'PURCHASE' THEN l.quantity_in
                    WHEN l.entry_date BETWEEN :from AND :to AND l.transaction_type = 'PURCHASE_REVERSAL' THEN -l.quantity_out
                    ELSE 0 END), 0) AS purchase_qty,
                  COALESCE(SUM(CASE
                    WHEN l.entry_date BETWEEN :from AND :to AND l.transaction_type = 'PURCHASE_RETURN' THEN l.quantity_out
                    ELSE 0 END), 0) AS purchase_return_qty,
                  COALESCE(SUM(CASE
                    WHEN l.entry_date BETWEEN :from AND :to AND l.transaction_type = 'SALE' THEN l.quantity_out
                    WHEN l.entry_date BETWEEN :from AND :to AND l.transaction_type = 'SALE_REVERSAL' THEN -l.quantity_in
                    ELSE 0 END), 0) AS sales_qty,
                  COALESCE(SUM(CASE
                    WHEN l.entry_date BETWEEN :from AND :to AND l.transaction_type = 'SALES_RETURN' THEN l.quantity_in
                    ELSE 0 END), 0) AS sales_return_qty,
                  COALESCE(SUM(CASE
                    WHEN l.entry_date BETWEEN :from AND :to AND l.transaction_type IN ('STOCK_ADJUSTMENT', 'OPENING_STOCK')
                      THEN l.quantity_in - l.quantity_out
                    WHEN l.entry_date BETWEEN :from AND :to AND l.transaction_type = 'STOCK_TRANSFER_IN' THEN l.quantity_in
                    WHEN l.entry_date BETWEEN :from AND :to AND l.transaction_type = 'STOCK_TRANSFER_OUT' THEN -l.quantity_out
                    ELSE 0 END), 0) AS adjustment_qty,
                  COALESCE(SUM(CASE
                    WHEN l.entry_date <= :to THEN CASE WHEN l.quantity_out > 0 THEN -l.total_value ELSE l.total_value END
                    ELSE 0 END), 0) AS stock_value
                FROM products p
                JOIN categories c ON c.id = p.category_id
                LEFT JOIN stock_ledger l
                  ON l.product_id = p.id AND l.is_deleted = 0 AND l.entry_date <= :to
                WHERE p.is_deleted = 0
                  AND p.stock_type <> 'NONE'
                  AND (
                    :search IS NULL
                    OR p.name %s
                    OR p.product_code %s
                    OR IFNULL(p.barcode, '') %s
                    OR c.name %s
                  )
                GROUP BY p.id, p.name, p.product_code
                HAVING opening_stock <> 0
                    OR purchase_qty <> 0
                    OR purchase_return_qty <> 0
                    OR sales_qty <> 0
                    OR sales_return_qty <> 0
                    OR adjustment_qty <> 0
                    OR stock_value <> 0
                """.formatted(LIKE, LIKE, LIKE, LIKE);
    }

    private String stockSql() {
        return """
                SELECT
                  p.id AS product_id,
                  p.product_code,
                  p.name AS product_name,
                  c.id AS category_id,
                  c.name AS category_name,
                  p.reorder_level,
                  COALESCE(m.qty, 0) - CASE WHEN :applyReserved = 1 THEN COALESCE(r.reserved_qty, 0) ELSE 0 END AS available,
                  COALESCE(m.stock_value, 0) AS stock_value
                FROM products p
                JOIN categories c ON c.id = p.category_id
                LEFT JOIN (
                  SELECT product_id,
                         SUM(quantity_in - quantity_out) AS qty,
                         SUM(CASE WHEN quantity_out > 0 THEN -total_value ELSE total_value END) AS stock_value
                  FROM stock_ledger
                  WHERE is_deleted = 0 AND entry_date <= :to
                  GROUP BY product_id
                ) m ON m.product_id = p.id
                LEFT JOIN (
                  SELECT product_id, SUM(reserved_quantity) AS reserved_qty
                  FROM inventory_balance
                  WHERE is_deleted = 0
                  GROUP BY product_id
                ) r ON r.product_id = p.id
                WHERE p.is_deleted = 0 AND p.stock_type <> 'NONE'
                """;
    }

    private Map<String, BigDecimal> amounts(String sql, MapSqlParameterSource params) {
        Map<String, BigDecimal> values = new HashMap<>();
        jdbc.query(sql, params, rs -> {
            values.put(rs.getString("bucket"), money(rs, "amount"));
        });
        return values;
    }

    private void addAmounts(Map<String, BigDecimal> net, String sql, MapSqlParameterSource params, boolean add) {
        jdbc.query(sql, params, rs -> {
            String label = rs.getString("label");
            BigDecimal amount = money(rs, "amount");
            BigDecimal current = net.getOrDefault(label, ProfitMath.ZERO);
            net.put(label, add ? current.add(amount) : current.subtract(amount));
        });
    }

    private List<AmountPoint> topAmounts(Map<String, BigDecimal> net, int limit) {
        return net.entrySet().stream()
                .filter(entry -> entry.getValue().signum() != 0)
                .sorted((left, right) -> right.getValue().compareTo(left.getValue()))
                .limit(limit)
                .map(entry -> new AmountPoint(entry.getKey(), entry.getKey(), entry.getValue()))
                .toList();
    }

    private void accumulateProduct(Map<String, ProductPoint> net, ResultSet rs, boolean add) throws SQLException {
        String label = rs.getString("label");
        BigDecimal amount = money(rs, "amount");
        BigDecimal quantity = money(rs, "quantity");
        ProductPoint current = net.getOrDefault(label, new ProductPoint(label, label, ProfitMath.ZERO, ProfitMath.ZERO));
        if (add) {
            net.put(label, new ProductPoint(label, label, current.amount().add(amount), current.quantity().add(quantity)));
        } else {
            net.put(label, new ProductPoint(label, label, current.amount().subtract(amount), current.quantity().subtract(quantity)));
        }
    }

    private List<RecentDocument> documents(String sql, MapSqlParameterSource params) {
        return jdbc.query(sql, params, (rs, row) -> new RecentDocument(
                rs.getLong("id"),
                rs.getString("number"),
                rs.getDate("dated").toLocalDate(),
                rs.getString("party"),
                money(rs, "amount"),
                rs.getString("status")));
    }

    private List<PartyRank> ranks(String sql, MapSqlParameterSource params) {
        return jdbc.query(sql, params, (rs, row) -> new PartyRank(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getLong("documents"),
                money(rs, "amount")));
    }

    private static String dateBucket(String column, ReportRanges.Grain grain) {
        String pattern = switch (grain) {
            case DAY -> "%Y-%m-%d";
            case MONTH -> "%Y-%m";
            case YEAR -> "%Y";
        };
        return "DATE_FORMAT(" + column + ", '" + pattern + "')";
    }

    static String order(Map<String, String> allowed, String sort, String fallbackColumn, String idColumn) {
        String column = fallbackColumn;
        boolean descending = true;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            String mapped = allowed.get(parts[0]);
            if (mapped == null) {
                throw new BusinessRuleException("INVALID_SORT", "sort", "That sort field is not supported");
            }
            column = mapped;
            descending = parts.length < 2 || !"asc".equalsIgnoreCase(parts[1]);
        }
        String direction = descending ? " DESC" : " ASC";
        return column + direction + ", " + idColumn + direction;
    }

    private static MapSqlParameterSource dates(LocalDate from, LocalDate to) {
        return new MapSqlParameterSource().addValue("from", from).addValue("to", to);
    }

    private static MapSqlParameterSource stockParams(LocalDate to, boolean applyReserved) {
        return new MapSqlParameterSource()
                .addValue("to", to)
                .addValue("applyReserved", applyReserved ? 1 : 0);
    }

    private static MapSqlParameterSource documentParams(DocumentQuery query) {
        return new MapSqlParameterSource()
                .addValue("from", query.from())
                .addValue("to", query.to())
                .addValue("search", query.search())
                .addValue("postedOnly", query.postedOnly())
                .addValue("status", query.status());
    }

    private static BigDecimal money(ResultSet rs, String column) throws SQLException {
        return ProfitMath.money(rs.getBigDecimal(column));
    }

    private static InventoryTotals emptyInventory() {
        return new InventoryTotals(
                ProfitMath.ZERO, ProfitMath.ZERO, ProfitMath.ZERO, ProfitMath.ZERO,
                ProfitMath.ZERO, ProfitMath.ZERO, ProfitMath.ZERO, ProfitMath.ZERO);
    }

    public record FlowTotals(
            BigDecimal grossSales,
            BigDecimal salesReturns,
            BigDecimal grossPurchases,
            BigDecimal issuedCost,
            BigDecimal restoredCost,
            BigDecimal expenses,
            BigDecimal receivables,
            BigDecimal payables) {
    }

    public record StockSnapshot(BigDecimal stockValue, long lowStock, long outOfStock) {
    }

    public record StockBoard(
            StockSnapshot snapshot,
            List<AmountPoint> byCategory,
            List<StockAlert> lowStock,
            List<StockAlert> outOfStock) {
    }

    private record StockPositionRow(
            long productId,
            String productName,
            String categoryName,
            BigDecimal available,
            BigDecimal reorderLevel,
            BigDecimal stockValue) {
    }

    public record CostParts(BigDecimal issued, BigDecimal restored) {
    }

    public record DocumentQuery(LocalDate from, LocalDate to, String search, int postedOnly, String status) {
    }

    public record InventoryPage(List<InventoryRow> rows, InventoryTotals totals, long total) {
    }
}
