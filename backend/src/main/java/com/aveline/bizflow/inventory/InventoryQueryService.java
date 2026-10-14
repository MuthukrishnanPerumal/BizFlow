package com.aveline.bizflow.inventory;

import com.aveline.bizflow.common.api.LikePatterns;
import com.aveline.bizflow.common.api.PageResponse;
import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.common.exception.ResourceNotFoundException;
import com.aveline.bizflow.inventory.InventoryReadModel.LastActivity;
import com.aveline.bizflow.inventory.InventoryReadModel.LedgerRow;
import com.aveline.bizflow.inventory.InventoryReadModel.StockPosition;
import com.aveline.bizflow.inventory.dto.InventoryDtos.BalanceResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.DashboardResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.LedgerEntryResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.MovementMixResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.OutOfStockResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.ProductStockResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.StockPositionResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.ValuationReportResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.ValuationRowResponse;
import com.aveline.bizflow.inventory.dto.InventoryDtos.ValuationSummaryResponse;
import com.aveline.bizflow.master.StockType;
import com.aveline.bizflow.master.catalog.Product;
import com.aveline.bizflow.master.catalog.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class InventoryQueryService {

    private static final Map<String, String> STOCK_SORTS = Map.of(
            "productName", "product_name",
            "productCode", "product_code",
            "currentStock", "current_stock",
            "availableStock", "available_stock",
            "stockValue", "stock_value",
            "reorderLevel", "reorder_level",
            "shortage", "shortage_quantity",
            "status", "stock_status",
            "sellingPrice", "selling_price");
    private static final Map<String, String> LEDGER_SORTS = Map.of(
            "date", "e.entry_date",
            "productName", "p.name",
            "quantityIn", "e.quantity_in",
            "quantityOut", "e.quantity_out",
            "runningBalance", "e.running_balance",
            "totalValue", "e.total_value",
            "transactionType", "e.transaction_type");

    private final InventoryReadModel readModel;
    private final InventoryBalanceRepository balanceRepository;
    private final ProductRepository productRepository;
    private final InventoryPolicy policy;
    private final Clock clock;

    public InventoryQueryService(
            InventoryReadModel readModel,
            InventoryBalanceRepository balanceRepository,
            ProductRepository productRepository,
            InventoryPolicy policy,
            Clock clock) {
        this.readModel = readModel;
        this.balanceRepository = balanceRepository;
        this.productRepository = productRepository;
        this.policy = policy;
        this.clock = clock;
    }

    public List<BalanceResponse> balances(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product was not found");
        }
        return balanceRepository.findByProductId(productId).stream().map(this::balance).toList();
    }

    public DashboardResponse dashboard(Long locationId) {
        InventoryReadModel.StockHealth health = readModel.health(
                InventoryReadModel.StockFilter.of(null, null, locationId, null, null));
        List<StockPositionResponse> low = readModel.positions(
                        InventoryReadModel.StockFilter.of(null, null, locationId, null, StockStatus.LOW_STOCK),
                        "shortage_quantity",
                        false,
                        5,
                        0)
                .stream()
                .map(this::position)
                .toList();
        List<OutOfStockResponse> out = outOfStock(null, null, locationId, 0, 5, "shortage,desc").items();
        List<LedgerEntryResponse> recent = readModel.ledger(
                        new InventoryReadModel.LedgerFilter(null, null, locationId, null, null, null),
                        "e.entry_date",
                        false,
                        8,
                        0)
                .stream()
                .map(this::ledger)
                .toList();
        List<MovementMixResponse> movement = readModel.movementMix(LocalDate.now(clock).minusDays(29), locationId).stream()
                .map(row -> new MovementMixResponse(row.transactionType(), row.quantityIn(), row.quantityOut(), row.movementCount()))
                .toList();
        return new DashboardResponse(
                policy.valuationMethod(),
                policy.allowNegativeStock(),
                health.skuCount(),
                health.unitsOnHand(),
                health.unitsReserved(),
                health.unitsAvailable(),
                health.inventoryValue(),
                health.inStock(),
                health.lowStock(),
                health.outOfStock(),
                health.overStock(),
                InventoryMath.healthPercent(health.inStock(), health.skuCount()),
                low,
                out,
                recent,
                movement);
    }

    public PageResponse<StockPositionResponse> overview(
            String search,
            Long categoryId,
            Long locationId,
            StockStatus status,
            int page,
            int size,
            String sort) {
        return positions(search, categoryId, locationId, status, page, size, sort, "product_name", true);
    }

    public PageResponse<StockPositionResponse> lowStock(
            String search,
            Long categoryId,
            Long locationId,
            int page,
            int size,
            String sort) {
        return positions(search, categoryId, locationId, StockStatus.LOW_STOCK, page, size, sort, "shortage_quantity", false);
    }

    public PageResponse<OutOfStockResponse> outOfStock(
            String search,
            Long categoryId,
            Long locationId,
            int page,
            int size,
            String sort) {
        PageResponse<StockPositionResponse> positions = positions(
                search, categoryId, locationId, StockStatus.OUT_OF_STOCK, page, size, sort, "product_name", true);
        Map<Long, LastActivity> activity = readModel.lastActivity(
                positions.items().stream().map(StockPositionResponse::productId).toList(),
                locationId);
        List<OutOfStockResponse> rows = positions.items().stream().map(row -> {
            LastActivity last = activity.getOrDefault(row.productId(), new LastActivity(null, null));
            return new OutOfStockResponse(
                    row.productId(),
                    row.productCode(),
                    row.barcode(),
                    row.productName(),
                    row.categoryName(),
                    row.currentStock(),
                    row.reorderLevel(),
                    row.shortageQuantity(),
                    last.lastPurchase(),
                    last.lastSale(),
                    row.status());
        }).toList();
        return new PageResponse<>(rows, positions.page(), positions.size(), positions.totalElements(), positions.totalPages());
    }

    public ValuationReportResponse valuation(
            String search,
            Long categoryId,
            Long locationId,
            int page,
            int size,
            String sort) {
        InventoryReadModel.StockFilter filter = InventoryReadModel.StockFilter.of(
                LikePatterns.contain(search), categoryId, locationId, null, null);
        InventoryReadModel.StockHealth health = readModel.health(filter);
        BigDecimal fifo = readModel.fifoValue(filter);
        ValuationMethod method = policy.valuationMethod();
        BigDecimal official = method == ValuationMethod.FIFO ? fifo : health.inventoryValue();
        PageResponse<StockPositionResponse> positions = positions(
                search, categoryId, locationId, null, page, size, sort, "stock_value", false);
        Map<Long, BigDecimal> fifoByProduct = readModel.fifoByProduct(
                positions.items().stream().map(StockPositionResponse::productId).toList(),
                locationId);
        List<ValuationRowResponse> rows = positions.items().stream().map(row -> {
            BigDecimal fifoValue = fifoByProduct.getOrDefault(row.productId(), InventoryMath.ZERO);
            BigDecimal officialValue = method == ValuationMethod.FIFO ? fifoValue : row.stockValue();
            return new ValuationRowResponse(
                    row.productId(),
                    row.productCode(),
                    row.productName(),
                    row.categoryName(),
                    row.currentStock(),
                    row.averageCost(),
                    row.stockValue(),
                    fifoValue,
                    officialValue);
        }).toList();
        return new ValuationReportResponse(
                new ValuationSummaryResponse(
                        method,
                        health.skuCount(),
                        health.unitsOnHand(),
                        health.inventoryValue(),
                        fifo,
                        official),
                rows,
                positions.page(),
                positions.size(),
                positions.totalElements(),
                positions.totalPages());
    }

    public PageResponse<LedgerEntryResponse> ledger(
            String search,
            Long productId,
            Long locationId,
            StockTxnType transactionType,
            LocalDate dateFrom,
            LocalDate dateTo,
            int page,
            int size,
            String sort) {
        dates(dateFrom, dateTo);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Sort sortSpec = this.sort(sort, LEDGER_SORTS, "e.entry_date", false);
        InventoryReadModel.LedgerFilter filter = new InventoryReadModel.LedgerFilter(
                LikePatterns.contain(search),
                productId,
                locationId,
                transactionType == null ? null : transactionType.name(),
                dateFrom,
                dateTo);
        long total = readModel.countLedger(filter);
        List<LedgerEntryResponse> items = readModel.ledger(filter, sortSpec.column(), sortSpec.ascending(), safeSize, safePage * safeSize)
                .stream()
                .map(this::ledger)
                .toList();
        return page(items, safePage, safeSize, total);
    }

    public ProductStockResponse product(Long productId, Long locationId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product was not found"));
        if (product.getStockType() == StockType.NONE) {
            throw new BusinessRuleException("STOCK_TYPE", "productId", product.getName() + " is not a stocked product");
        }
        List<StockPosition> rows = readModel.positions(
                InventoryReadModel.StockFilter.of(null, null, locationId, productId, null),
                "product_name",
                true,
                1,
                0);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Product was not found");
        }
        LastActivity activity = readModel.lastActivity(List.of(productId), locationId)
                .getOrDefault(productId, new LastActivity(null, null));
        List<LedgerEntryResponse> movements = readModel.ledger(
                        new InventoryReadModel.LedgerFilter(null, productId, locationId, null, null, null),
                        "e.entry_date",
                        false,
                        20,
                        0)
                .stream()
                .map(this::ledger)
                .toList();
        List<BalanceResponse> buckets = balances(productId).stream()
                .filter(bucket -> locationId == null || locationId.equals(bucket.locationId()))
                .toList();
        return new ProductStockResponse(position(rows.get(0)), buckets, movements, activity.lastPurchase(), activity.lastSale());
    }

    private PageResponse<StockPositionResponse> positions(
            String search,
            Long categoryId,
            Long locationId,
            StockStatus status,
            int page,
            int size,
            String sort,
            String defaultColumn,
            boolean defaultAscending) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Sort sortSpec = this.sort(sort, STOCK_SORTS, defaultColumn, defaultAscending);
        InventoryReadModel.StockFilter filter = InventoryReadModel.StockFilter.of(
                LikePatterns.contain(search), categoryId, locationId, null, status);
        long total = readModel.countPositions(filter);
        List<StockPositionResponse> items = readModel.positions(
                        filter, sortSpec.column(), sortSpec.ascending(), safeSize, safePage * safeSize)
                .stream()
                .map(this::position)
                .toList();
        return page(items, safePage, safeSize, total);
    }

    private BalanceResponse balance(InventoryBalance balance) {
        BigDecimal available = InventoryMath.available(balance.getQuantityOnHand(), balance.getReservedQuantity());
        return new BalanceResponse(
                balance.getProduct().getId(),
                balance.getProduct().getProductCode(),
                balance.getProduct().getName(),
                balance.getBatchCode().isEmpty() ? null : balance.getBatchCode(),
                balance.getExpiryDate(),
                balance.getQuantityOnHand(),
                balance.getUnitCost(),
                balance.getLocation().getId(),
                balance.getLocation().getLocationCode(),
                balance.getLocation().getName(),
                balance.getLocation().isDefaultLocation(),
                balance.getReservedQuantity(),
                available);
    }

    private StockPositionResponse position(StockPosition row) {
        return new StockPositionResponse(
                row.productId(),
                row.productCode(),
                row.barcode(),
                row.productName(),
                row.categoryName(),
                row.currentStock(),
                row.reservedStock(),
                row.availableStock(),
                row.averageCost(),
                row.sellingPrice(),
                row.stockValue(),
                row.reorderLevel(),
                row.maximumStockLevel(),
                row.status(),
                row.shortageQuantity());
    }

    private LedgerEntryResponse ledger(LedgerRow row) {
        return new LedgerEntryResponse(
                row.id(),
                row.entryDate(),
                row.productId(),
                row.productCode(),
                row.productName(),
                row.transactionType(),
                row.referenceNumber(),
                row.quantityIn(),
                row.quantityOut(),
                row.runningBalance(),
                row.unitCost(),
                row.totalValue(),
                row.user(),
                row.remarks(),
                row.locationId(),
                row.locationCode(),
                row.locationName(),
                row.batchCode());
    }

    private void dates(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("DATE_RANGE", "dateFrom", "The start date cannot be after the end date");
        }
    }

    private Sort sort(String sort, Map<String, String> allowed, String defaultColumn, boolean defaultAscending) {
        if (sort == null || sort.isBlank()) {
            return new Sort(defaultColumn, defaultAscending);
        }
        String[] parts = sort.split(",", 2);
        String column = allowed.get(parts[0]);
        if (column == null) {
            throw new BusinessRuleException("INVALID_SORT", "sort", "That sort field is not supported");
        }
        boolean ascending = parts.length > 1 && "asc".equalsIgnoreCase(parts[1]);
        if (parts.length < 2) {
            ascending = false;
        }
        return new Sort(column, ascending);
    }

    private <T> PageResponse<T> page(List<T> items, int page, int size, long total) {
        int totalPages = total == 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageResponse<>(items, page, size, total, totalPages);
    }

    private record Sort(String column, boolean ascending) {
    }
}
