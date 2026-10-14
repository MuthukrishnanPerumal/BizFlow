package com.aveline.bizflow.inventory;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class InventoryReadModel {

    private static final String STATUS_CASE = """
            CASE
              WHEN g.available_stock <= 0 THEN 'OUT_OF_STOCK'
              WHEN g.maximum_stock_level IS NOT NULL
                   AND g.maximum_stock_level > 0
                   AND g.available_stock > g.maximum_stock_level THEN 'OVER_STOCK'
              WHEN g.available_stock <= g.reorder_level THEN 'LOW_STOCK'
              ELSE 'IN_STOCK'
            END
            """;

    private static final String GROUPED = """
            SELECT
              p.id AS product_id,
              p.product_code AS product_code,
              p.barcode AS barcode,
              p.name AS product_name,
              c.name AS category_name,
              COALESCE(SUM(b.quantity_on_hand), 0) AS current_stock,
              COALESCE(SUM(b.reserved_quantity), 0) AS reserved_stock,
              COALESCE(SUM(b.quantity_on_hand), 0) - COALESCE(SUM(b.reserved_quantity), 0) AS available_stock,
              CASE
                WHEN COALESCE(SUM(b.quantity_on_hand), 0) = 0 THEN 0
                ELSE ROUND(SUM(b.quantity_on_hand * b.unit_cost) / SUM(b.quantity_on_hand), 4)
              END AS average_cost,
              p.selling_price AS selling_price,
              CASE
                WHEN COUNT(b.id) = 0 THEN 0
                ELSE ROUND(COALESCE(SUM(b.quantity_on_hand * b.unit_cost), 0), 4)
              END AS stock_value,
              p.reorder_level AS reorder_level,
              p.maximum_stock_level AS maximum_stock_level
            FROM products p
            JOIN categories c ON c.id = p.category_id
            LEFT JOIN inventory_balance b
              ON b.product_id = p.id
             AND b.is_deleted = 0
             AND (:locationId IS NULL OR b.location_id = :locationId)
            WHERE p.is_deleted = 0
              AND p.stock_type <> 'NONE'
              AND (:categoryId IS NULL OR p.category_id = :categoryId)
              AND (:productId IS NULL OR p.id = :productId)
              AND (
                :search IS NULL
                OR p.name LIKE CONCAT('%', :search, '%') ESCAPE '\\\\'
                OR p.product_code LIKE CONCAT('%', :search, '%') ESCAPE '\\\\'
                OR IFNULL(p.barcode, '') LIKE CONCAT('%', :search, '%') ESCAPE '\\\\'
                OR c.name LIKE CONCAT('%', :search, '%') ESCAPE '\\\\'
              )
            GROUP BY p.id, c.id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public InventoryReadModel(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countPositions(StockFilter filter) {
        String sql = "SELECT COUNT(*) FROM (" + positioned(filter) + ") counted";
        Long count = jdbc.queryForObject(sql, filter.source(), Long.class);
        return count == null ? 0 : count;
    }

    public List<StockPosition> positions(StockFilter filter, String orderColumn, boolean ascending, int limit, int offset) {
        String direction = ascending ? "ASC" : "DESC";
        String sql = """
                SELECT * FROM (%s) page
                ORDER BY %s %s, product_id ASC
                LIMIT %d OFFSET %d
                """.formatted(positioned(filter), orderColumn, direction, limit, offset);
        return jdbc.query(sql, filter.source(), (rs, row) -> mapPosition(rs));
    }

    public StockHealth health(StockFilter filter) {
        String sql = """
                SELECT
                  COUNT(*) AS sku_count,
                  COALESCE(SUM(current_stock), 0) AS units_on_hand,
                  COALESCE(SUM(reserved_stock), 0) AS units_reserved,
                  COALESCE(SUM(available_stock), 0) AS units_available,
                  COALESCE(SUM(stock_value), 0) AS inventory_value,
                  COALESCE(SUM(stock_status = 'IN_STOCK'), 0) AS in_stock,
                  COALESCE(SUM(stock_status = 'LOW_STOCK'), 0) AS low_stock,
                  COALESCE(SUM(stock_status = 'OUT_OF_STOCK'), 0) AS out_of_stock,
                  COALESCE(SUM(stock_status = 'OVER_STOCK'), 0) AS over_stock
                FROM (%s) health
                """.formatted(positioned(filter));
        return jdbc.queryForObject(sql, filter.source(), (rs, row) -> new StockHealth(
                rs.getLong("sku_count"),
                rs.getBigDecimal("units_on_hand"),
                rs.getBigDecimal("units_reserved"),
                rs.getBigDecimal("units_available"),
                rs.getBigDecimal("inventory_value"),
                rs.getLong("in_stock"),
                rs.getLong("low_stock"),
                rs.getLong("out_of_stock"),
                rs.getLong("over_stock")));
    }

    public BigDecimal fifoValue(StockFilter filter) {
        String sql = """
                SELECT COALESCE(SUM(l.quantity_remaining * l.unit_cost), 0)
                FROM inventory_cost_layer l
                JOIN products p ON p.id = l.product_id AND p.is_deleted = 0 AND p.stock_type <> 'NONE'
                JOIN categories c ON c.id = p.category_id
                WHERE l.is_deleted = 0
                  AND l.quantity_remaining > 0
                  AND (:locationId IS NULL OR l.location_id = :locationId)
                  AND (:categoryId IS NULL OR p.category_id = :categoryId)
                  AND (:productId IS NULL OR p.id = :productId)
                  AND (
                    :search IS NULL
                    OR p.name LIKE CONCAT('%', :search, '%') ESCAPE '\\\\'
                    OR p.product_code LIKE CONCAT('%', :search, '%') ESCAPE '\\\\'
                    OR IFNULL(p.barcode, '') LIKE CONCAT('%', :search, '%') ESCAPE '\\\\'
                    OR c.name LIKE CONCAT('%', :search, '%') ESCAPE '\\\\'
                  )
                """;
        BigDecimal value = jdbc.queryForObject(sql, filter.source(), BigDecimal.class);
        return value == null ? InventoryMath.ZERO : value;
    }

    public Map<Long, BigDecimal> fifoByProduct(Collection<Long> productIds, Long locationId) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", productIds)
                .addValue("locationId", locationId);
        String sql = """
                SELECT product_id, COALESCE(SUM(quantity_remaining * unit_cost), 0) AS fifo_value
                FROM inventory_cost_layer
                WHERE is_deleted = 0
                  AND quantity_remaining > 0
                  AND product_id IN (:ids)
                  AND (:locationId IS NULL OR location_id = :locationId)
                GROUP BY product_id
                """;
        Map<Long, BigDecimal> values = new HashMap<>();
        jdbc.query(sql, params, rs -> {
            values.put(rs.getLong("product_id"), rs.getBigDecimal("fifo_value"));
        });
        return values;
    }

    public Map<Long, LastActivity> lastActivity(Collection<Long> productIds, Long locationId) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ids", productIds)
                .addValue("locationId", locationId);
        String sql = """
                SELECT product_id, transaction_type, MAX(entry_date) AS last_date
                FROM stock_ledger
                WHERE is_deleted = 0
                  AND product_id IN (:ids)
                  AND transaction_type IN ('PURCHASE', 'SALE')
                  AND (:locationId IS NULL OR location_id = :locationId)
                GROUP BY product_id, transaction_type
                """;
        Map<Long, LastActivity> activity = new HashMap<>();
        jdbc.query(sql, params, rs -> {
            long productId = rs.getLong("product_id");
            LastActivity current = activity.getOrDefault(productId, new LastActivity(null, null));
            LocalDate date = rs.getDate("last_date").toLocalDate();
            if ("PURCHASE".equals(rs.getString("transaction_type"))) {
                activity.put(productId, new LastActivity(date, current.lastSale()));
            } else {
                activity.put(productId, new LastActivity(current.lastPurchase(), date));
            }
        });
        return activity;
    }

    public long countLedger(LedgerFilter filter) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) " + ledgerFrom(),
                filter.source(),
                Long.class);
        return count == null ? 0 : count;
    }

    public List<LedgerRow> ledger(LedgerFilter filter, String orderColumn, boolean ascending, int limit, int offset) {
        String direction = ascending ? "ASC" : "DESC";
        String sql = """
                SELECT
                  e.id,
                  e.entry_date,
                  p.id AS product_id,
                  p.product_code,
                  p.name AS product_name,
                  e.transaction_type,
                  e.reference_type,
                  e.reference_id,
                  e.reference_number,
                  e.quantity_in,
                  e.quantity_out,
                  e.running_balance,
                  e.unit_cost,
                  e.total_value,
                  e.created_by,
                  e.remarks,
                  l.id AS location_id,
                  l.location_code,
                  l.name AS location_name,
                  e.batch_code
                %s
                ORDER BY %s %s, e.id ASC
                LIMIT %d OFFSET %d
                """.formatted(ledgerFrom(), orderColumn, direction, limit, offset);
        return jdbc.query(sql, filter.source(), (rs, row) -> new LedgerRow(
                rs.getLong("id"),
                rs.getDate("entry_date").toLocalDate(),
                rs.getLong("product_id"),
                rs.getString("product_code"),
                rs.getString("product_name"),
                StockTxnType.valueOf(rs.getString("transaction_type")),
                rs.getString("reference_number"),
                rs.getBigDecimal("quantity_in"),
                rs.getBigDecimal("quantity_out"),
                rs.getBigDecimal("running_balance"),
                rs.getBigDecimal("unit_cost"),
                rs.getBigDecimal("total_value"),
                rs.getString("created_by"),
                rs.getString("remarks"),
                rs.getLong("location_id"),
                rs.getString("location_code"),
                rs.getString("location_name"),
                rs.getString("batch_code")));
    }

    public List<MovementMix> movementMix(LocalDate from, Long locationId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromDate", Date.valueOf(from))
                .addValue("locationId", locationId);
        String sql = """
                SELECT
                  transaction_type,
                  COALESCE(SUM(quantity_in), 0) AS quantity_in,
                  COALESCE(SUM(quantity_out), 0) AS quantity_out,
                  COUNT(*) AS movement_count
                FROM stock_ledger
                WHERE is_deleted = 0
                  AND entry_date >= :fromDate
                  AND (:locationId IS NULL OR location_id = :locationId)
                GROUP BY transaction_type
                ORDER BY transaction_type
                """;
        return jdbc.query(sql, params, (rs, row) -> new MovementMix(
                StockTxnType.valueOf(rs.getString("transaction_type")),
                rs.getBigDecimal("quantity_in"),
                rs.getBigDecimal("quantity_out"),
                rs.getLong("movement_count")));
    }

    private String positioned(StockFilter filter) {
        return """
                SELECT
                  g.product_id,
                  g.product_code,
                  g.barcode,
                  g.product_name,
                  g.category_name,
                  g.current_stock,
                  g.reserved_stock,
                  g.available_stock,
                  g.average_cost,
                  g.selling_price,
                  g.stock_value,
                  g.reorder_level,
                  g.maximum_stock_level,
                  %s AS stock_status,
                  GREATEST(g.reorder_level - g.available_stock, 0) AS shortage_quantity
                FROM (%s) g
                WHERE (:status IS NULL OR %s = :status)
                """.formatted(STATUS_CASE, GROUPED, STATUS_CASE);
    }

    private String ledgerFrom() {
        return """
                FROM stock_ledger e
                JOIN products p ON p.id = e.product_id
                JOIN stock_location l ON l.id = e.location_id
                WHERE e.is_deleted = 0
                  AND (:productId IS NULL OR e.product_id = :productId)
                  AND (:locationId IS NULL OR e.location_id = :locationId)
                  AND (:transactionType IS NULL OR e.transaction_type = :transactionType)
                  AND (:dateFrom IS NULL OR e.entry_date >= :dateFrom)
                  AND (:dateTo IS NULL OR e.entry_date <= :dateTo)
                  AND (
                    :search IS NULL
                    OR p.name LIKE CONCAT('%', :search, '%') ESCAPE '\\\\'
                    OR p.product_code LIKE CONCAT('%', :search, '%') ESCAPE '\\\\'
                    OR IFNULL(e.reference_number, '') LIKE CONCAT('%', :search, '%') ESCAPE '\\\\'
                    OR IFNULL(e.remarks, '') LIKE CONCAT('%', :search, '%') ESCAPE '\\\\'
                  )
                """;
    }

    private StockPosition mapPosition(ResultSet rs) throws SQLException {
        return new StockPosition(
                rs.getLong("product_id"),
                rs.getString("product_code"),
                rs.getString("barcode"),
                rs.getString("product_name"),
                rs.getString("category_name"),
                rs.getBigDecimal("current_stock"),
                rs.getBigDecimal("reserved_stock"),
                rs.getBigDecimal("available_stock"),
                rs.getBigDecimal("average_cost"),
                rs.getBigDecimal("selling_price"),
                rs.getBigDecimal("stock_value"),
                rs.getBigDecimal("reorder_level"),
                rs.getBigDecimal("maximum_stock_level"),
                StockStatus.valueOf(rs.getString("stock_status")),
                rs.getBigDecimal("shortage_quantity"));
    }

    public record StockFilter(String search, Long categoryId, Long locationId, Long productId, String status) {
        static StockFilter of(String search, Long categoryId, Long locationId, Long productId, StockStatus status) {
            return new StockFilter(search, categoryId, locationId, productId, status == null ? null : status.name());
        }

        MapSqlParameterSource source() {
            return new MapSqlParameterSource()
                    .addValue("search", search)
                    .addValue("categoryId", categoryId)
                    .addValue("locationId", locationId)
                    .addValue("productId", productId)
                    .addValue("status", status);
        }
    }

    public record LedgerFilter(
            String search,
            Long productId,
            Long locationId,
            String transactionType,
            LocalDate dateFrom,
            LocalDate dateTo) {

        MapSqlParameterSource source() {
            return new MapSqlParameterSource()
                    .addValue("search", search)
                    .addValue("productId", productId)
                    .addValue("locationId", locationId)
                    .addValue("transactionType", transactionType)
                    .addValue("dateFrom", dateFrom == null ? null : Date.valueOf(dateFrom))
                    .addValue("dateTo", dateTo == null ? null : Date.valueOf(dateTo));
        }
    }

    public record StockPosition(
            long productId,
            String productCode,
            String barcode,
            String productName,
            String categoryName,
            BigDecimal currentStock,
            BigDecimal reservedStock,
            BigDecimal availableStock,
            BigDecimal averageCost,
            BigDecimal sellingPrice,
            BigDecimal stockValue,
            BigDecimal reorderLevel,
            BigDecimal maximumStockLevel,
            StockStatus status,
            BigDecimal shortageQuantity) {
    }

    public record StockHealth(
            long skuCount,
            BigDecimal unitsOnHand,
            BigDecimal unitsReserved,
            BigDecimal unitsAvailable,
            BigDecimal inventoryValue,
            long inStock,
            long lowStock,
            long outOfStock,
            long overStock) {
    }

    public record LedgerRow(
            long id,
            LocalDate entryDate,
            long productId,
            String productCode,
            String productName,
            StockTxnType transactionType,
            String referenceNumber,
            BigDecimal quantityIn,
            BigDecimal quantityOut,
            BigDecimal runningBalance,
            BigDecimal unitCost,
            BigDecimal totalValue,
            String user,
            String remarks,
            long locationId,
            String locationCode,
            String locationName,
            String batchCode) {
    }

    public record MovementMix(
            StockTxnType transactionType,
            BigDecimal quantityIn,
            BigDecimal quantityOut,
            long movementCount) {
    }

    public record LastActivity(LocalDate lastPurchase, LocalDate lastSale) {
    }
}
