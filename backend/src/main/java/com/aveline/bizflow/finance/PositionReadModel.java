package com.aveline.bizflow.finance;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.finance.dto.FinanceDtos.PayableReport;
import com.aveline.bizflow.finance.dto.FinanceDtos.PayableRow;
import com.aveline.bizflow.finance.dto.FinanceDtos.ReceivableReport;
import com.aveline.bizflow.finance.dto.FinanceDtos.ReceivableRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

@Repository
public class PositionReadModel {

    private static final String TEMPLATE = """
            SELECT
              party.party_id,
              party.party_code,
              party.party_name,
              party.payment_terms,
              party.total_documents,
              party.total_returns,
              party.total_payments,
              party.outstanding,
              party.overdue_supported,
              party.overdue_amount,
              CASE
                WHEN party.overdue_amount > 0 THEN 'OVERDUE'
                WHEN party.outstanding > 0 THEN 'OPEN'
                WHEN party.outstanding < 0 THEN 'CREDIT'
                ELSE 'SETTLED'
              END AS position_status
            FROM (
              SELECT
                p.id AS party_id,
                p.{{codeColumn}} AS party_code,
                p.{{nameColumn}} AS party_name,
                p.payment_terms AS payment_terms,
                COALESCE(doc.total_documents, 0) AS total_documents,
                COALESCE(ret.total_returns, 0) AS total_returns,
                COALESCE(pay.total_payments, 0) AS total_payments,
                {{outstanding}} AS outstanding,
                CASE WHEN p.payment_terms REGEXP '[0-9]' THEN 1 ELSE 0 END AS overdue_supported,
                CASE
                  WHEN p.payment_terms REGEXP '[0-9]' THEN COALESCE(ov.overdue_amount, 0)
                  ELSE NULL
                END AS overdue_amount
              FROM {{partyTable}} p
              LEFT JOIN (
                SELECT {{partyIdColumn}} AS party_id, SUM(grand_total) AS total_documents
                FROM {{documentTable}}
                WHERE is_deleted = 0
                  AND status NOT IN ('DRAFT', 'CANCELLED')
                  AND (:fromDate IS NULL OR {{documentDate}} >= :fromDate)
                  AND (:toDate IS NULL OR {{documentDate}} <= :toDate)
                GROUP BY {{partyIdColumn}}
              ) doc ON doc.party_id = p.id
              LEFT JOIN (
                SELECT {{partyIdColumn}} AS party_id, SUM(grand_total) AS total_returns
                FROM {{returnTable}}
                WHERE is_deleted = 0
                  AND (:fromDate IS NULL OR {{returnDate}} >= :fromDate)
                  AND (:toDate IS NULL OR {{returnDate}} <= :toDate)
                GROUP BY {{partyIdColumn}}
              ) ret ON ret.party_id = p.id
              LEFT JOIN (
                SELECT {{paymentPartyColumn}} AS party_id,
                       SUM(CASE
                         WHEN status = 'POSTED' AND direction = '{{receiptDirection}}' THEN amount
                         WHEN status = 'POSTED' AND direction <> '{{receiptDirection}}' THEN -amount
                         ELSE 0
                       END) AS total_payments
                FROM payments
                WHERE is_deleted = 0
                  AND {{paymentPartyColumn}} IS NOT NULL
                  AND (:fromDate IS NULL OR payment_date >= :fromDate)
                  AND (:toDate IS NULL OR payment_date <= :toDate)
                GROUP BY {{paymentPartyColumn}}
              ) pay ON pay.party_id = p.id
              LEFT JOIN (
                SELECT {{partyIdColumn}} AS party_id,
                       COALESCE(SUM(debit_amount), 0) AS debit_total,
                       COALESCE(SUM(credit_amount), 0) AS credit_total
                FROM {{ledgerTable}}
                WHERE is_deleted = 0
                  AND (:toDate IS NULL OR entry_date <= :toDate)
                GROUP BY {{partyIdColumn}}
              ) led ON led.party_id = p.id
              LEFT JOIN (
                SELECT d.{{partyIdColumn}} AS party_id, SUM({{openBalance}}) AS overdue_amount
                FROM {{documentTable}} d
                JOIN {{partyTable}} party_terms ON party_terms.id = d.{{partyIdColumn}}
                WHERE d.is_deleted = 0
                  AND party_terms.is_deleted = 0
                  AND d.status IN ('CONFIRMED', 'COMPLETED', 'PARTIALLY_RETURNED', 'RETURNED')
                  AND party_terms.payment_terms REGEXP '[0-9]'
                  AND DATE_ADD(
                        d.{{documentDate}},
                        INTERVAL CAST(REGEXP_SUBSTR(party_terms.payment_terms, '[0-9]+') AS UNSIGNED) DAY
                      ) < :asOf
                GROUP BY d.{{partyIdColumn}}
              ) ov ON ov.party_id = p.id
              WHERE p.is_deleted = 0
                AND (:partyId IS NULL OR p.id = :partyId)
                AND (
                  :search IS NULL
                  OR lower(p.{{nameColumn}}) LIKE lower(concat('%', :search, '%')) ESCAPE '\\\\'
                  OR lower(p.{{codeColumn}}) LIKE lower(concat('%', :search, '%')) ESCAPE '\\\\'
                )
            ) party
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PositionReadModel(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ReceivableReport receivables(
            Long customerId,
            String search,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate asOf,
            PositionStatus status,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            int page,
            int size,
            String sort) {
        Query query = query(Side.RECEIVABLE, customerId, search, fromDate, toDate, asOf, status, minAmount, maxAmount, page, size, sort);
        List<ReceivableRow> rows = jdbc.query(query.sql(), query.params(), (rs, row) -> receivable(rs));
        Totals totals = totals(Side.RECEIVABLE, customerId, search, fromDate, toDate, asOf, status, minAmount, maxAmount);
        return new ReceivableReport(
                rows,
                query.page(),
                query.size(),
                totals.count(),
                pages(totals.count(), query.size()),
                totals.outstanding(),
                totals.overdue(),
                totals.documents(),
                totals.payments(),
                totals.overdueParties());
    }

    public PayableReport payables(
            Long supplierId,
            String search,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate asOf,
            PositionStatus status,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            int page,
            int size,
            String sort) {
        Query query = query(Side.PAYABLE, supplierId, search, fromDate, toDate, asOf, status, minAmount, maxAmount, page, size, sort);
        List<PayableRow> rows = jdbc.query(query.sql(), query.params(), (rs, row) -> payable(rs));
        Totals totals = totals(Side.PAYABLE, supplierId, search, fromDate, toDate, asOf, status, minAmount, maxAmount);
        return new PayableReport(
                rows,
                query.page(),
                query.size(),
                totals.count(),
                pages(totals.count(), query.size()),
                totals.outstanding(),
                totals.overdue(),
                totals.documents(),
                totals.payments(),
                totals.overdueParties());
    }

    private Query query(
            Side side,
            Long partyId,
            String search,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate asOf,
            PositionStatus status,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            int page,
            int size,
            String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String sql = filtered(side) + " ORDER BY " + order(sort) + " LIMIT :limit OFFSET :offset";
        MapSqlParameterSource params = params(partyId, search, fromDate, toDate, asOf, status, minAmount, maxAmount);
        params.addValue("limit", safeSize);
        params.addValue("offset", safePage * safeSize);
        return new Query(sql, params, safePage, safeSize);
    }

    private Totals totals(
            Side side,
            Long partyId,
            String search,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate asOf,
            PositionStatus status,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        String sql = """
                SELECT
                  COUNT(*) AS parties,
                  COALESCE(SUM(outstanding), 0) AS outstanding,
                  COALESCE(SUM(overdue_amount), 0) AS overdue,
                  COALESCE(SUM(total_documents), 0) AS documents,
                  COALESCE(SUM(total_payments), 0) AS payments,
                  COALESCE(SUM(CASE WHEN position_status = 'OVERDUE' THEN 1 ELSE 0 END), 0) AS overdue_parties
                FROM (
                """ + filtered(side) + ") totals";
        return jdbc.queryForObject(sql, params(partyId, search, fromDate, toDate, asOf, status, minAmount, maxAmount), (rs, row) -> new Totals(
                rs.getLong("parties"),
                rs.getBigDecimal("outstanding"),
                rs.getBigDecimal("overdue"),
                rs.getBigDecimal("documents"),
                rs.getBigDecimal("payments"),
                rs.getLong("overdue_parties")));
    }

    private String filtered(Side side) {
        return "SELECT * FROM (" + base(side) + """
                ) positioned
                WHERE (:status IS NULL OR position_status = :status)
                  AND (:minAmount IS NULL OR outstanding >= :minAmount)
                  AND (:maxAmount IS NULL OR outstanding <= :maxAmount)
                """;
    }

    private String base(Side side) {
        return TEMPLATE
                .replace("{{codeColumn}}", side.codeColumn)
                .replace("{{nameColumn}}", side.nameColumn)
                .replace("{{outstanding}}", side.outstanding)
                .replace("{{partyTable}}", side.partyTable)
                .replace("{{partyIdColumn}}", side.partyIdColumn)
                .replace("{{documentTable}}", side.documentTable)
                .replace("{{documentDate}}", side.documentDate)
                .replace("{{returnTable}}", side.returnTable)
                .replace("{{returnDate}}", side.returnDate)
                .replace("{{paymentPartyColumn}}", side.partyIdColumn)
                .replace("{{receiptDirection}}", side.receiptDirection)
                .replace("{{ledgerTable}}", side.ledgerTable)
                .replace("{{openBalance}}", side.openBalance);
    }

    private MapSqlParameterSource params(
            Long partyId,
            String search,
            LocalDate fromDate,
            LocalDate toDate,
            LocalDate asOf,
            PositionStatus status,
            BigDecimal minAmount,
            BigDecimal maxAmount) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("partyId", partyId, Types.BIGINT);
        params.addValue("search", search, Types.VARCHAR);
        params.addValue("fromDate", fromDate == null ? null : Date.valueOf(fromDate), Types.DATE);
        params.addValue("toDate", toDate == null ? null : Date.valueOf(toDate), Types.DATE);
        params.addValue("asOf", Date.valueOf(asOf), Types.DATE);
        params.addValue("status", status == null ? null : status.name(), Types.VARCHAR);
        params.addValue("minAmount", minAmount, Types.DECIMAL);
        params.addValue("maxAmount", maxAmount, Types.DECIMAL);
        return params;
    }

    private String order(String sort) {
        String property = "outstanding";
        boolean descending = true;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            property = switch (parts[0]) {
                case "outstanding", "partyName", "totalSales", "totalPurchases", "totalPayments" -> parts[0];
                default -> throw new BusinessRuleException("INVALID_SORT", "That sort field is not supported");
            };
            descending = parts.length < 2 || !"asc".equalsIgnoreCase(parts[1]);
        }
        String column = switch (property) {
            case "partyName" -> "party_name";
            case "totalSales", "totalPurchases" -> "total_documents";
            case "totalPayments" -> "total_payments";
            default -> "outstanding";
        };
        return column + (descending ? " DESC" : " ASC") + ", party_name ASC";
    }

    private ReceivableRow receivable(ResultSet rs) throws SQLException {
        return new ReceivableRow(
                rs.getLong("party_id"),
                rs.getString("party_code"),
                rs.getString("party_name"),
                rs.getString("payment_terms"),
                rs.getBigDecimal("total_documents"),
                rs.getBigDecimal("total_returns"),
                rs.getBigDecimal("total_payments"),
                rs.getBigDecimal("outstanding"),
                rs.getInt("overdue_supported") == 1,
                overdue(rs),
                PositionStatus.valueOf(rs.getString("position_status")));
    }

    private PayableRow payable(ResultSet rs) throws SQLException {
        return new PayableRow(
                rs.getLong("party_id"),
                rs.getString("party_code"),
                rs.getString("party_name"),
                rs.getString("payment_terms"),
                rs.getBigDecimal("total_documents"),
                rs.getBigDecimal("total_returns"),
                rs.getBigDecimal("total_payments"),
                rs.getBigDecimal("outstanding"),
                rs.getInt("overdue_supported") == 1,
                overdue(rs),
                PositionStatus.valueOf(rs.getString("position_status")));
    }

    private BigDecimal overdue(ResultSet rs) throws SQLException {
        if (rs.getInt("overdue_supported") == 0) {
            return null;
        }
        return rs.getBigDecimal("overdue_amount");
    }

    private int pages(long total, int size) {
        if (total == 0) {
            return 0;
        }
        return (int) ((total + size - 1) / size);
    }

    private enum Side {
        RECEIVABLE(
                "customers",
                "customer_id",
                "customer_code",
                "customer_name",
                "sales",
                "invoice_date",
                "sales_returns",
                "return_date",
                "customer_ledger_entry",
                "IN",
                "(COALESCE(led.debit_total, 0) - COALESCE(led.credit_total, 0))",
                "GREATEST(0, d.grand_total - d.paid_amount - d.returned_value + d.refunded_amount)"),
        PAYABLE(
                "suppliers",
                "supplier_id",
                "supplier_code",
                "supplier_name",
                "purchases",
                "purchase_date",
                "purchase_returns",
                "return_date",
                "supplier_ledger_entry",
                "OUT",
                "(COALESCE(led.credit_total, 0) - COALESCE(led.debit_total, 0))",
                "GREATEST(0, d.grand_total - d.paid_amount - d.returned_value)");

        private final String partyTable;
        private final String partyIdColumn;
        private final String codeColumn;
        private final String nameColumn;
        private final String documentTable;
        private final String documentDate;
        private final String returnTable;
        private final String returnDate;
        private final String ledgerTable;
        private final String receiptDirection;
        private final String outstanding;
        private final String openBalance;

        Side(
                String partyTable,
                String partyIdColumn,
                String codeColumn,
                String nameColumn,
                String documentTable,
                String documentDate,
                String returnTable,
                String returnDate,
                String ledgerTable,
                String receiptDirection,
                String outstanding,
                String openBalance) {
            this.partyTable = partyTable;
            this.partyIdColumn = partyIdColumn;
            this.codeColumn = codeColumn;
            this.nameColumn = nameColumn;
            this.documentTable = documentTable;
            this.documentDate = documentDate;
            this.returnTable = returnTable;
            this.returnDate = returnDate;
            this.ledgerTable = ledgerTable;
            this.receiptDirection = receiptDirection;
            this.outstanding = outstanding;
            this.openBalance = openBalance;
        }
    }

    private record Query(String sql, MapSqlParameterSource params, int page, int size) {
    }

    private record Totals(
            long count,
            BigDecimal outstanding,
            BigDecimal overdue,
            BigDecimal documents,
            BigDecimal payments,
            long overdueParties) {
    }
}
