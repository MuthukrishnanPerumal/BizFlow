package com.aveline.bizflow.reporting;

import com.aveline.bizflow.common.api.LikePatterns;
import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.export.ExportFormat;
import com.aveline.bizflow.export.TabularExport;
import com.aveline.bizflow.export.TabularSheet;
import com.aveline.bizflow.reporting.ReportRanges.Grain;
import com.aveline.bizflow.reporting.ReportRanges.Range;
import com.aveline.bizflow.reporting.ReportingReadModel.CostParts;
import com.aveline.bizflow.reporting.ReportingReadModel.DocumentQuery;
import com.aveline.bizflow.reporting.ReportingReadModel.FlowTotals;
import com.aveline.bizflow.reporting.ReportingReadModel.InventoryPage;
import com.aveline.bizflow.reporting.ReportingReadModel.StockBoard;
import com.aveline.bizflow.reporting.ReportingReadModel.StockSnapshot;
import com.aveline.bizflow.reporting.dto.ReportingDtos.AmountPoint;
import com.aveline.bizflow.reporting.dto.ReportingDtos.ChartView;
import com.aveline.bizflow.reporting.dto.ReportingDtos.DashboardResponse;
import com.aveline.bizflow.reporting.dto.ReportingDtos.DraftCounts;
import com.aveline.bizflow.reporting.dto.ReportingDtos.InventoryReport;
import com.aveline.bizflow.reporting.dto.ReportingDtos.KpiView;
import com.aveline.bizflow.reporting.dto.ReportingDtos.PeriodView;
import com.aveline.bizflow.reporting.dto.ReportingDtos.ProfitPoint;
import com.aveline.bizflow.reporting.dto.ReportingDtos.ProfitReport;
import com.aveline.bizflow.reporting.dto.ReportingDtos.ProfitRow;
import com.aveline.bizflow.reporting.dto.ReportingDtos.PurchaseReport;
import com.aveline.bizflow.reporting.dto.ReportingDtos.SalesReport;
import com.aveline.bizflow.reporting.dto.ReportingDtos.StatementView;
import com.aveline.bizflow.reporting.dto.ReportingDtos.WidgetView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ReportingQueryService {

    static final int PAGE_LIMIT = 100;
    static final int EXPORT_LIMIT = 10_000;
    private static final int WIDGET_LIMIT = 6;
    private static final int CHART_LIMIT = 8;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final Set<String> PROFIT_SORTS = Set.of(
            "period", "revenue", "costOfGoodsSold", "grossProfit", "expenses", "netProfit");

    private final ReportingReadModel readModel;
    private final Clock clock;
    private final TabularExport tabularExport;

    public ReportingQueryService(ReportingReadModel readModel, Clock clock, TabularExport tabularExport) {
        this.readModel = readModel;
        this.clock = clock;
        this.tabularExport = tabularExport;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(ReportPreset preset, LocalDate from, LocalDate to) {
        Range range = ReportRanges.resolve(preset, from, to, clock);
        FlowTotals flow = readModel.flow(range.from(), range.to());
        boolean reserved = applyReserved(range.to());
        StockBoard stock = readModel.stockBoard(range.to(), reserved, CHART_LIMIT, WIDGET_LIMIT);
        StockSnapshot snapshot = stock.snapshot();
        ProfitMath.Statement statement = statement(flow);
        TrendBundle trends = trendBundle(range);
        return new DashboardResponse(
                period(range),
                new KpiView(
                        statement.grossSales(),
                        ProfitMath.money(flow.grossPurchases()),
                        statement.grossProfit(),
                        statement.netProfit(),
                        snapshot.stockValue(),
                        snapshot.lowStock(),
                        snapshot.outOfStock(),
                        flow.receivables(),
                        flow.payables(),
                        statement.expenses()),
                view(statement),
                charts(range, stock, trends),
                widgets(range, stock),
                new DraftCounts(
                        readModel.countDraftSales(range.from(), range.to()),
                        readModel.countDraftPurchases(range.from(), range.to())));
    }

    @Transactional(readOnly = true)
    public SalesReport sales(
            ReportPreset preset,
            LocalDate from,
            LocalDate to,
            String search,
            DocumentScope scope,
            int page,
            int size,
            String sort) {
        return sales(preset, from, to, search, scope, page, size, sort, false);
    }

    @Transactional(readOnly = true)
    public String salesCsv(
            ReportPreset preset,
            LocalDate from,
            LocalDate to,
            String search,
            DocumentScope scope,
            String sort) {
        SalesReport report = sales(preset, from, to, search, scope, 0, EXPORT_LIMIT, sort, true);
        guardExport(report.totalElements());
        return ReportCsv.sales(report);
    }

    @Transactional(readOnly = true)
    public PurchaseReport purchases(
            ReportPreset preset,
            LocalDate from,
            LocalDate to,
            String search,
            DocumentScope scope,
            int page,
            int size,
            String sort) {
        return purchases(preset, from, to, search, scope, page, size, sort, false);
    }

    @Transactional(readOnly = true)
    public String purchasesCsv(
            ReportPreset preset,
            LocalDate from,
            LocalDate to,
            String search,
            DocumentScope scope,
            String sort) {
        PurchaseReport report = purchases(preset, from, to, search, scope, 0, EXPORT_LIMIT, sort, true);
        guardExport(report.totalElements());
        return ReportCsv.purchases(report);
    }

    @Transactional(readOnly = true)
    public InventoryReport inventory(
            ReportPreset preset,
            LocalDate from,
            LocalDate to,
            String search,
            int page,
            int size,
            String sort) {
        return inventory(preset, from, to, search, page, size, sort, false);
    }

    @Transactional(readOnly = true)
    public InventoryReport inventoryPrint(ReportPreset preset, LocalDate from, LocalDate to, String search, String sort) {
        return inventory(preset, from, to, search, 0, EXPORT_LIMIT, sort, true);
    }

    @Transactional(readOnly = true)
    public byte[] salesFile(ExportFormat format, ReportPreset preset, LocalDate from, LocalDate to, String search, DocumentScope scope, String sort) {
        SalesReport report = sales(preset, from, to, search, scope, 0, EXPORT_LIMIT, sort, true);
        return tabularExport.write(format, sheet("Sales report",
                List.of("invoice", "date", "customer", "subtotal", "discount", "tax", "total", "paid", "balance", "status"),
                report.items().stream().map(item -> List.of(
                        text(item.invoice()), text(item.date()), text(item.customer()), money(item.subtotal()), money(item.discount()),
                        money(item.tax()), money(item.total()), money(item.paid()), money(item.balance()), text(item.status()))).toList()));
    }

    @Transactional(readOnly = true)
    public byte[] purchasesFile(ExportFormat format, ReportPreset preset, LocalDate from, LocalDate to, String search, DocumentScope scope, String sort) {
        PurchaseReport report = purchases(preset, from, to, search, scope, 0, EXPORT_LIMIT, sort, true);
        return tabularExport.write(format, sheet("Purchase report",
                List.of("invoice", "date", "supplier", "subtotal", "discount", "tax", "total", "paid", "balance", "status"),
                report.items().stream().map(item -> List.of(
                        text(item.invoice()), text(item.date()), text(item.supplier()), money(item.subtotal()), money(item.discount()),
                        money(item.tax()), money(item.total()), money(item.paid()), money(item.balance()), text(item.status()))).toList()));
    }

    @Transactional(readOnly = true)
    public byte[] inventoryFile(ExportFormat format, ReportPreset preset, LocalDate from, LocalDate to, String search, String sort) {
        InventoryReport report = inventory(preset, from, to, search, 0, EXPORT_LIMIT, sort, true);
        return tabularExport.write(format, sheet("Stock report",
                List.of("product", "opening stock", "purchase", "purchase return", "sales", "sales return", "adjustment", "closing stock", "stock value"),
                report.items().stream().map(item -> List.of(
                        text(item.product()), money(item.openingStock()), money(item.purchase()), money(item.purchaseReturn()),
                        money(item.sales()), money(item.salesReturn()), money(item.adjustment()), money(item.closingStock()),
                        money(item.stockValue()))).toList()));
    }

    @Transactional(readOnly = true)
    public byte[] profitFile(ExportFormat format, ReportPreset preset, LocalDate from, LocalDate to, String search, String sort) {
        ProfitReport report = profit(preset, from, to, search, 0, EXPORT_LIMIT, sort, true);
        return tabularExport.write(format, sheet("Profit report",
                List.of("period", "revenue", "cost of goods sold", "gross profit", "expenses", "net profit"),
                report.items().stream().map(item -> List.of(
                        text(item.period()), money(item.revenue()), money(item.costOfGoodsSold()), money(item.grossProfit()),
                        money(item.expenses()), money(item.netProfit()))).toList()));
    }

    @Transactional(readOnly = true)
    public String inventoryCsv(ReportPreset preset, LocalDate from, LocalDate to, String search, String sort) {
        InventoryReport report = inventory(preset, from, to, search, 0, EXPORT_LIMIT, sort, true);
        guardExport(report.totalElements());
        return ReportCsv.inventory(report);
    }

    @Transactional(readOnly = true)
    public ProfitReport profit(
            ReportPreset preset,
            LocalDate from,
            LocalDate to,
            String search,
            int page,
            int size,
            String sort) {
        return profit(preset, from, to, search, page, size, sort, false);
    }

    @Transactional(readOnly = true)
    public String profitCsv(ReportPreset preset, LocalDate from, LocalDate to, String search, String sort) {
        ProfitReport report = profit(preset, from, to, search, 0, EXPORT_LIMIT, sort, true);
        guardExport(report.totalElements());
        return ReportCsv.profit(report);
    }

    private SalesReport sales(
            ReportPreset preset,
            LocalDate from,
            LocalDate to,
            String search,
            DocumentScope scope,
            int page,
            int size,
            String sort,
            boolean export) {
        Range range = ReportRanges.resolve(preset, from, to, clock);
        DocumentQuery query = documents(range, search, scope);
        Window window = window(page, size, export);
        long total = readModel.countSales(query);
        if (export) {
            guardExport(total);
        }
        return new SalesReport(
                period(range),
                readModel.salesPage(query, sort, window.size(), window.offset()),
                readModel.salesTotals(query),
                window.page(),
                window.size(),
                total,
                pages(total, window.size()));
    }

    private PurchaseReport purchases(
            ReportPreset preset,
            LocalDate from,
            LocalDate to,
            String search,
            DocumentScope scope,
            int page,
            int size,
            String sort,
            boolean export) {
        Range range = ReportRanges.resolve(preset, from, to, clock);
        DocumentQuery query = documents(range, search, scope);
        Window window = window(page, size, export);
        long total = readModel.countPurchases(query);
        if (export) {
            guardExport(total);
        }
        return new PurchaseReport(
                period(range),
                readModel.purchasePage(query, sort, window.size(), window.offset()),
                readModel.purchaseTotals(query),
                window.page(),
                window.size(),
                total,
                pages(total, window.size()));
    }

    private InventoryReport inventory(
            ReportPreset preset,
            LocalDate from,
            LocalDate to,
            String search,
            int page,
            int size,
            String sort,
            boolean export) {
        Range range = ReportRanges.resolve(preset, from, to, clock);
        DocumentQuery query = documents(range, search, DocumentScope.ALL);
        Window window = window(page, size, export);
        InventoryPage pageResult = readModel.inventory(query, sort, window.size(), window.offset());
        if (export) {
            guardExport(pageResult.total());
        }
        return new InventoryReport(
                period(range),
                pageResult.rows(),
                pageResult.totals(),
                window.page(),
                window.size(),
                pageResult.total(),
                pages(pageResult.total(), window.size()));
    }

    private ProfitReport profit(
            ReportPreset preset,
            LocalDate from,
            LocalDate to,
            String search,
            int page,
            int size,
            String sort,
            boolean export) {
        Range range = ReportRanges.resolve(preset, from, to, clock);
        List<ProfitBucket> buckets = profitBuckets(range);
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ENGLISH);
        if (!term.isEmpty()) {
            buckets = buckets.stream()
                    .filter(bucket -> bucket.key().contains(term) || bucket.label().toLowerCase(Locale.ENGLISH).contains(term))
                    .toList();
        }
        List<ProfitBucket> ordered = new ArrayList<>(buckets);
        ordered.sort(profitOrder(sort));
        Window window = window(page, size, export);
        int start = Math.min(window.offset(), ordered.size());
        int end = Math.min(start + window.size(), ordered.size());
        List<ProfitRow> rows = ordered.subList(start, end).stream()
                .map(bucket -> new ProfitRow(
                        bucket.label(),
                        bucket.statement().revenue(),
                        bucket.statement().costOfGoodsSold(),
                        bucket.statement().grossProfit(),
                        bucket.statement().expenses(),
                        bucket.statement().netProfit()))
                .toList();
        if (export) {
            guardExport(ordered.size());
        }
        return new ProfitReport(
                period(range),
                view(combine(ordered)),
                rows,
                window.page(),
                window.size(),
                ordered.size(),
                pages(ordered.size(), window.size()));
    }

    private List<ProfitBucket> profitBuckets(Range range) {
        TrendBundle trends = trendBundle(range);
        List<ProfitBucket> buckets = new ArrayList<>();
        for (String key : ReportRanges.buckets(range.from(), range.to(), range.grain())) {
            CostParts cost = trends.costs().getOrDefault(key, new CostParts(ProfitMath.ZERO, ProfitMath.ZERO));
            ProfitMath.Statement statement = ProfitMath.statement(
                    trends.sales().get(key),
                    trends.returns().get(key),
                    cost.issued(),
                    cost.restored(),
                    trends.expenses().get(key));
            if (!ProfitMath.idle(statement)) {
                buckets.add(new ProfitBucket(key, label(key, range.grain()), statement));
            }
        }
        return buckets;
    }

    private ChartView charts(Range range, StockBoard stock, TrendBundle trends) {
        return new ChartView(
                trend(trends.sales(), range),
                trend(trends.purchases(), range),
                profitTrend(range, trends),
                readModel.salesByCategory(range.from(), range.to(), CHART_LIMIT),
                readModel.topProducts(range.from(), range.to(), CHART_LIMIT),
                readModel.purchasesBySupplier(range.from(), range.to(), CHART_LIMIT),
                stock.byCategory(),
                readModel.paymentsByMethod(range.from(), range.to()));
    }

    private WidgetView widgets(Range range, StockBoard stock) {
        return new WidgetView(
                readModel.recentSales(range.from(), range.to(), WIDGET_LIMIT),
                readModel.recentPurchases(range.from(), range.to(), WIDGET_LIMIT),
                stock.lowStock(),
                stock.outOfStock(),
                readModel.topCustomers(range.from(), range.to(), WIDGET_LIMIT),
                readModel.topSuppliers(range.from(), range.to(), WIDGET_LIMIT),
                readModel.recentPayments(range.from(), range.to(), WIDGET_LIMIT),
                readModel.recentMovements(range.from(), range.to(), WIDGET_LIMIT));
    }

    private TrendBundle trendBundle(Range range) {
        return new TrendBundle(
                readModel.salesByBucket(range.from(), range.to(), range.grain()),
                readModel.purchaseByBucket(range.from(), range.to(), range.grain()),
                readModel.salesReturnsByBucket(range.from(), range.to(), range.grain()),
                readModel.cogsByBucket(range.from(), range.to(), range.grain()),
                readModel.expensesByBucket(range.from(), range.to(), range.grain()));
    }

    private List<AmountPoint> trend(Map<String, BigDecimal> values, Range range) {
        return ReportRanges.buckets(range.from(), range.to(), range.grain()).stream()
                .map(key -> new AmountPoint(key, label(key, range.grain()), ProfitMath.money(values.get(key))))
                .toList();
    }

    private List<ProfitPoint> profitTrend(Range range, TrendBundle trends) {
        return ReportRanges.buckets(range.from(), range.to(), range.grain()).stream().map(key -> {
            CostParts cost = trends.costs().getOrDefault(key, new CostParts(ProfitMath.ZERO, ProfitMath.ZERO));
            ProfitMath.Statement statement = ProfitMath.statement(
                    trends.sales().get(key),
                    trends.returns().get(key),
                    cost.issued(),
                    cost.restored(),
                    trends.expenses().get(key));
            return new ProfitPoint(
                    key,
                    label(key, range.grain()),
                    statement.revenue(),
                    statement.costOfGoodsSold(),
                    statement.grossProfit(),
                    statement.netProfit());
        }).toList();
    }

    private ProfitMath.Statement combine(List<ProfitBucket> buckets) {
        BigDecimal gross = ProfitMath.ZERO;
        BigDecimal returns = ProfitMath.ZERO;
        BigDecimal cogs = ProfitMath.ZERO;
        BigDecimal expenses = ProfitMath.ZERO;
        for (ProfitBucket bucket : buckets) {
            gross = gross.add(bucket.statement().grossSales());
            returns = returns.add(bucket.statement().salesReturns());
            cogs = cogs.add(bucket.statement().costOfGoodsSold());
            expenses = expenses.add(bucket.statement().expenses());
        }
        return ProfitMath.statement(gross, returns, cogs, ProfitMath.ZERO, expenses);
    }

    private Comparator<ProfitBucket> profitOrder(String sort) {
        String field = "period";
        boolean descending = false;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            if (!PROFIT_SORTS.contains(parts[0])) {
                throw new BusinessRuleException("INVALID_SORT", "sort", "That sort field is not supported");
            }
            field = parts[0];
            descending = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]);
        }
        Comparator<ProfitBucket> comparator = switch (field) {
            case "revenue" -> Comparator.comparing(bucket -> bucket.statement().revenue());
            case "costOfGoodsSold" -> Comparator.comparing(bucket -> bucket.statement().costOfGoodsSold());
            case "grossProfit" -> Comparator.comparing(bucket -> bucket.statement().grossProfit());
            case "expenses" -> Comparator.comparing(bucket -> bucket.statement().expenses());
            case "netProfit" -> Comparator.comparing(bucket -> bucket.statement().netProfit());
            default -> Comparator.comparing(ProfitBucket::key);
        };
        return descending ? comparator.reversed() : comparator;
    }

    private DocumentQuery documents(Range range, String search, DocumentScope scope) {
        DocumentScope selected = scope == null ? DocumentScope.POSTED : scope;
        int postedOnly = selected == DocumentScope.POSTED ? 1 : 0;
        String status = selected == DocumentScope.POSTED || selected == DocumentScope.ALL ? null : selected.name();
        return new DocumentQuery(range.from(), range.to(), LikePatterns.contain(search), postedOnly, status);
    }

    private boolean applyReserved(LocalDate to) {
        return !to.isBefore(LocalDate.now(clock));
    }

    private static ProfitMath.Statement statement(FlowTotals flow) {
        return ProfitMath.statement(
                flow.grossSales(),
                flow.salesReturns(),
                flow.issuedCost(),
                flow.restoredCost(),
                flow.expenses());
    }

    private static StatementView view(ProfitMath.Statement statement) {
        return new StatementView(
                statement.grossSales(),
                statement.salesReturns(),
                statement.revenue(),
                statement.costOfGoodsSold(),
                statement.grossProfit(),
                statement.expenses(),
                statement.netProfit());
    }

    private static PeriodView period(Range range) {
        return new PeriodView(range.preset().name(), range.from(), range.to(), range.grain().name());
    }

    private static String label(String key, Grain grain) {
        return switch (grain) {
            case DAY -> LocalDate.parse(key).format(DAY);
            case MONTH -> YearMonth.parse(key).format(MONTH);
            case YEAR -> key;
        };
    }

    private static Window window(int page, int size, boolean export) {
        int safePage = Math.max(page, 0);
        int cap = export ? EXPORT_LIMIT : PAGE_LIMIT;
        int safeSize = Math.min(Math.max(size, 1), cap);
        return new Window(safePage, safeSize, safePage * safeSize);
    }

    private static int pages(long total, int size) {
        if (total <= 0) {
            return 0;
        }
        return (int) ((total + size - 1) / size);
    }

    private static TabularSheet sheet(String title, List<String> headers, List<List<String>> rows) {
        return new TabularSheet(title, headers, rows);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String money(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static void guardExport(long total) {
        if (total > EXPORT_LIMIT) {
            throw new BusinessRuleException(
                    "EXPORT_LIMIT",
                    "size",
                    "Narrow the date range before exporting more than 10,000 rows");
        }
    }

    private record Window(int page, int size, int offset) {
    }

    private record ProfitBucket(String key, String label, ProfitMath.Statement statement) {
    }

    private record TrendBundle(
            Map<String, BigDecimal> sales,
            Map<String, BigDecimal> purchases,
            Map<String, BigDecimal> returns,
            Map<String, CostParts> costs,
            Map<String, BigDecimal> expenses) {
    }
}
