package com.aveline.bizflow.reporting;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportingIntegrationTest {

    private static final String INITIAL_PASSWORD = "Aveline#Admin1";
    private static final String REPLACEMENT_PASSWORD = "Aveline#Admin2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.aveline.bizflow.identity.RoleRepository roleRepository;

    @Test
    void emptyRangeIsZeroAndUnauthorizedUsersCannotReadFigures() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/reports/sales")).andExpect(status().isUnauthorized());

        String admin = adminToken();
        mockMvc.perform(get("/api/v1/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", "1980-01-01")
                        .param("to", "1980-01-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.totalSales").value(0))
                .andExpect(jsonPath("$.data.kpis.totalPurchases").value(0))
                .andExpect(jsonPath("$.data.kpis.grossProfit").value(0))
                .andExpect(jsonPath("$.data.kpis.netProfit").value(0))
                .andExpect(jsonPath("$.data.kpis.expenses").value(0))
                .andExpect(jsonPath("$.data.kpis.receivables").value(0))
                .andExpect(jsonPath("$.data.kpis.payables").value(0))
                .andExpect(jsonPath("$.data.kpis.totalStockValue").value(0))
                .andExpect(jsonPath("$.data.kpis.lowStockItems").value(0))
                .andExpect(jsonPath("$.data.statement.revenue").value(0))
                .andExpect(jsonPath("$.data.widgets.recentSales", hasSize(0)))
                .andExpect(jsonPath("$.data.widgets.recentPurchases", hasSize(0)))
                .andExpect(jsonPath("$.data.charts.salesByCategory", hasSize(0)));
        mockMvc.perform(get("/api/v1/reports/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", "1980-01-01")
                        .param("to", "1980-01-07"))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.totals.total").value(0));
        mockMvc.perform(get("/api/v1/reports/profit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", "1980-01-01")
                        .param("to", "1980-01-07"))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.statement.netProfit").value(0));
        mockMvc.perform(get("/api/v1/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", "2026-10-03")
                        .param("to", "2026-10-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("DATE_RANGE"));
        mockMvc.perform(get("/api/v1/reports/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("sort", "nope"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_SORT"));

        long roleId = roleRepository.findByCode("INVENTORY_USER").orElseThrow().getId();
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"stock.reports",
                                  "fullName":"Stock Reports",
                                  "email":"stock.reports@aveline.local",
                                  "password":"Viewer#Pass1",
                                  "status":"ACTIVE",
                                  "roleIds":[%d]
                                }
                                """.formatted(roleId)))
                .andExpect(status().isCreated());
        String stock = userToken("stock.reports", "Viewer#Pass1", "Viewer#Pass2");
        mockMvc.perform(get("/api/v1/dashboard").header(HttpHeaders.AUTHORIZATION, "Bearer " + stock))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/reports/profit").header(HttpHeaders.AUTHORIZATION, "Bearer " + stock))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardAndReportsUseTheSameProfitAndDocumentTotals() throws Exception {
        String admin = adminToken();
        LocalDate date = LocalDate.of(2099, 5, 1 + Math.floorMod(System.nanoTime(), 27));
        String day = date.toString();
        Books books = books(admin, day);

        mockMvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expenseDate":"%s","category":"RENT","description":"Store rent","amount":"10.0000","paymentMethod":"CASH"}
                                """.formatted(day)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/customer-payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%d,"paymentDate":"%s","amount":"12.0000","paymentMethod":"CASH","referenceNumber":"RPT-CASH"}
                                """.formatted(books.customerId(), day)))
                .andExpect(status().isCreated());

        MvcResult dashboard = mockMvc.perform(get("/api/v1/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", day)
                        .param("to", day))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period.from").value(day))
                .andExpect(jsonPath("$.data.period.to").value(day))
                .andExpect(jsonPath("$.data.kpis.totalSales").value(80.0))
                .andExpect(jsonPath("$.data.kpis.totalPurchases").value(100.0))
                .andExpect(jsonPath("$.data.kpis.expenses").value(10.0))
                .andExpect(jsonPath("$.data.kpis.receivables").value(80.0))
                .andExpect(jsonPath("$.data.kpis.payables").value(100.0))
                .andExpect(jsonPath("$.data.statement.revenue").value(80.0))
                .andExpect(jsonPath("$.data.statement.costOfGoodsSold").value(25.0))
                .andExpect(jsonPath("$.data.statement.grossProfit").value(55.0))
                .andExpect(jsonPath("$.data.kpis.grossProfit").value(55.0))
                .andExpect(jsonPath("$.data.statement.netProfit").value(45.0))
                .andExpect(jsonPath("$.data.kpis.netProfit").value(45.0))
                .andExpect(jsonPath("$.data.charts.salesTrend[0].amount").value(80.0))
                .andExpect(jsonPath("$.data.charts.purchaseTrend[0].amount").value(100.0))
                .andExpect(jsonPath("$.data.charts.profitTrend[0].netProfit").value(45.0))
                .andExpect(jsonPath("$.data.charts.salesByCategory[0].amount").value(80.0))
                .andExpect(jsonPath("$.data.charts.topProducts[0].quantity").value(1.0))
                .andExpect(jsonPath("$.data.charts.purchasesBySupplier[0].amount").value(100.0))
                .andExpect(jsonPath("$.data.charts.paymentBreakdown[0].key").value("CASH"))
                .andExpect(jsonPath("$.data.charts.paymentBreakdown[0].amount").value(12.0))
                .andExpect(jsonPath("$.data.widgets.recentSales", hasSize(1)))
                .andExpect(jsonPath("$.data.widgets.recentPurchases", hasSize(1)))
                .andExpect(jsonPath("$.data.widgets.recentPayments", hasSize(1)))
                .andExpect(jsonPath("$.data.widgets.recentMovements[0].type").value("SALE"))
                .andReturn();

        mockMvc.perform(get("/api/v1/reports/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", day)
                        .param("to", day)
                        .param("search", books.invoice()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].invoice").value(books.invoice()))
                .andExpect(jsonPath("$.data.items[0].customer").value(org.hamcrest.Matchers.startsWith("Report customer")))
                .andExpect(jsonPath("$.data.items[0].subtotal").value(80.0))
                .andExpect(jsonPath("$.data.items[0].discount").value(0))
                .andExpect(jsonPath("$.data.items[0].tax").value(0))
                .andExpect(jsonPath("$.data.items[0].total").value(80.0))
                .andExpect(jsonPath("$.data.items[0].paid").value(0))
                .andExpect(jsonPath("$.data.items[0].balance").value(80.0))
                .andExpect(jsonPath("$.data.items[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.totals.total").value(80.0));
        mockMvc.perform(get("/api/v1/reports/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "TODAY")
                        .param("search", books.invoice()))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/reports/purchases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", day)
                        .param("to", day)
                        .param("search", books.supplierInvoice()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totals.total").value(100.0))
                .andExpect(jsonPath("$.data.items[0].balance").value(100.0));

        MvcResult inventory = mockMvc.perform(get("/api/v1/reports/inventory")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", day)
                        .param("to", day)
                        .param("search", books.productCode()))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].openingStock").value(0))
                .andExpect(jsonPath("$.data.items[0].purchase").value(4.0))
                .andExpect(jsonPath("$.data.items[0].purchaseReturn").value(0))
                .andExpect(jsonPath("$.data.items[0].sales").value(1.0))
                .andExpect(jsonPath("$.data.items[0].salesReturn").value(0))
                .andExpect(jsonPath("$.data.items[0].adjustment").value(0))
                .andExpect(jsonPath("$.data.items[0].closingStock").value(3.0))
                .andExpect(jsonPath("$.data.items[0].stockValue").value(75.0))
                .andReturn();
        double opening = JsonPath.read(inventory.getResponse().getContentAsString(), "$.data.items[0].openingStock");
        double purchase = JsonPath.read(inventory.getResponse().getContentAsString(), "$.data.items[0].purchase");
        double purchaseReturn = JsonPath.read(inventory.getResponse().getContentAsString(), "$.data.items[0].purchaseReturn");
        double sales = JsonPath.read(inventory.getResponse().getContentAsString(), "$.data.items[0].sales");
        double salesReturn = JsonPath.read(inventory.getResponse().getContentAsString(), "$.data.items[0].salesReturn");
        double adjustment = JsonPath.read(inventory.getResponse().getContentAsString(), "$.data.items[0].adjustment");
        double closing = JsonPath.read(inventory.getResponse().getContentAsString(), "$.data.items[0].closingStock");
        assertEquals(opening + purchase - purchaseReturn - sales + salesReturn + adjustment, closing, 0.0001);

        mockMvc.perform(get("/api/v1/reports/profit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", day)
                        .param("to", day))
                .andExpect(jsonPath("$.data.statement.revenue").value(80.0))
                .andExpect(jsonPath("$.data.statement.costOfGoodsSold").value(25.0))
                .andExpect(jsonPath("$.data.statement.grossProfit").value(55.0))
                .andExpect(jsonPath("$.data.statement.expenses").value(10.0))
                .andExpect(jsonPath("$.data.statement.netProfit").value(45.0))
                .andExpect(jsonPath("$.data.items[0].netProfit").value(45.0));

        double dashboardStock = JsonPath.read(dashboard.getResponse().getContentAsString(), "$.data.kpis.totalStockValue");
        MvcResult stockReport = mockMvc.perform(get("/api/v1/reports/inventory")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", day)
                        .param("to", day)
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andReturn();
        double reportStock = JsonPath.read(stockReport.getResponse().getContentAsString(), "$.data.totals.stockValue");
        assertEquals(dashboardStock, reportStock, 0.001);

        mockMvc.perform(get("/api/v1/reports/sales.csv")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", day)
                        .param("to", day)
                        .param("search", books.invoice()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("sales-report.csv")))
                .andExpect(content().string(containsString("invoice,date,customer")))
                .andExpect(content().string(containsString(books.invoice())));
    }

    @Test
    void salesReportPagesFiltersAndSortsALargePostedSet() throws Exception {
        String admin = adminToken();
        String mark = suffix();
        long customerId = customer(admin, mark);
        LocalDate date = LocalDate.of(2099, 8, 1 + Math.floorMod(System.nanoTime(), 27));
        String prefix = "RPT" + mark;
        List<Object[]> batch = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (int index = 1; index <= 120; index++) {
            BigDecimal amount = BigDecimal.valueOf(index).setScale(4);
            running = running.add(amount);
            batch.add(new Object[]{prefix + String.format("%04d", index), customerId, date, "CONFIRMED", amount});
        }
        batch.add(new Object[]{prefix + "DRAFT", customerId, date, "DRAFT", new BigDecimal("9.0000")});
        batch.add(new Object[]{prefix + "CANCEL", customerId, date, "CANCELLED", new BigDecimal("8.0000")});
        jdbcTemplate.batchUpdate("""
                INSERT INTO sales (
                    sale_number, customer_id, invoice_date, payment_type, status,
                    subtotal, item_discount, order_discount, total_discount, tax_amount,
                    additional_charges, round_off, grand_total, paid_amount, balance_amount,
                    returned_value, refunded_amount,
                    created_at, updated_at, created_by, updated_by, is_deleted, version
                ) VALUES (
                    ?, ?, ?, 'CREDIT', ?,
                    ?, 0, 0, 0, 0,
                    0, 0, ?, 0, ?,
                    0, 0,
                    UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'test', 'test', 0, 0
                )
                """, batch, batch.size(), (statement, row) -> {
            statement.setString(1, (String) row[0]);
            statement.setLong(2, (Long) row[1]);
            statement.setObject(3, row[2]);
            statement.setString(4, (String) row[3]);
            statement.setBigDecimal(5, (BigDecimal) row[4]);
            statement.setBigDecimal(6, (BigDecimal) row[4]);
            statement.setBigDecimal(7, (BigDecimal) row[4]);
        });

        mockMvc.perform(get("/api/v1/reports/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", date.toString())
                        .param("to", date.toString())
                        .param("search", prefix)
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "total,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(120))
                .andExpect(jsonPath("$.data.totalPages").value(6))
                .andExpect(jsonPath("$.data.items", hasSize(20)))
                .andExpect(jsonPath("$.data.items[0].total").value(1.0))
                .andExpect(jsonPath("$.data.totals.total").value(running.doubleValue()));
        mockMvc.perform(get("/api/v1/reports/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", date.toString())
                        .param("to", date.toString())
                        .param("search", prefix)
                        .param("page", "5")
                        .param("size", "20")
                        .param("sort", "total,asc"))
                .andExpect(jsonPath("$.data.items", hasSize(20)))
                .andExpect(jsonPath("$.data.items[19].total").value(120.0));
        mockMvc.perform(get("/api/v1/reports/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", date.toString())
                        .param("to", date.toString())
                        .param("search", prefix)
                        .param("scope", "ALL"))
                .andExpect(jsonPath("$.data.totalElements").value(122));
        mockMvc.perform(get("/api/v1/reports/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", date.toString())
                        .param("to", date.toString())
                        .param("search", prefix + "DRAFT")
                        .param("scope", "DRAFT"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("DRAFT"));
        mockMvc.perform(get("/api/v1/reports/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", date.toString())
                        .param("to", date.toString())
                        .param("search", "no-such-invoice-" + prefix))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totals.total").value(0));
    }

    private Books books(String token, String day) throws Exception {
        String mark = suffix();
        Number categoryId = idOf(mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"RC" + mark + "\",\"name\":\"Report " + mark + "\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        Number brandId = idOf(mockMvc.perform(post("/api/v1/brands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"RB" + mark + "\",\"name\":\"Brand " + mark + "\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        Number unitId = JsonPath.read(mockMvc.perform(get("/api/v1/units")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("search", "PCS"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.items[0].id");
        String productCode = "RPT-" + mark;
        Number productId = idOf(mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode":"%s",
                                  "barcode":"76%s",
                                  "name":"Report item %s",
                                  "categoryId":%s,
                                  "brandId":%s,
                                  "unitId":%s,
                                  "taxPercent":"0.0000",
                                  "purchasePrice":"25.0000",
                                  "sellingPrice":"80.0000",
                                  "mrp":"100.0000",
                                  "minimumSellingPrice":"0.0000",
                                  "reorderLevel":"10",
                                  "stockType":"PICS",
                                  "batchTracking":false,
                                  "serialTracking":false,
                                  "expiryTracking":false,
                                  "status":"ACTIVE"
                                }
                                """.formatted(productCode, mark, mark, categoryId, brandId, unitId)))
                .andExpect(status().isCreated())
                .andReturn());
        Number supplierId = idOf(mockMvc.perform(post("/api/v1/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierCode":"RS%s",
                                  "supplierName":"Report supplier %s",
                                  "phone":"9876543210",
                                  "openingBalance":"0",
                                  "creditLimit":"50000",
                                  "paymentTerms":"30",
                                  "status":"ACTIVE"
                                }
                                """.formatted(mark, mark)))
                .andExpect(status().isCreated())
                .andReturn());
        Number customerId = idOf(mockMvc.perform(post("/api/v1/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerCode":"RC%s",
                                  "customerName":"Report customer %s",
                                  "phone":"9876500000",
                                  "openingBalance":"0",
                                  "creditLimit":"50000",
                                  "paymentTerms":"30",
                                  "status":"ACTIVE"
                                }
                                """.formatted(mark, mark)))
                .andExpect(status().isCreated())
                .andReturn());
        String supplierInvoice = "PINV-" + mark;
        mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId":%s,
                                  "invoiceNumber":"%s",
                                  "purchaseDate":"%s",
                                  "paymentType":"CREDIT",
                                  "paidAmount":"0",
                                  "confirm":true,
                                  "items":[{
                                    "productId":%s,
                                    "quantity":"4",
                                    "freeQuantity":"0",
                                    "purchasePrice":"25.0000",
                                    "discount":"0",
                                    "tax":"0"
                                  }]
                                }
                                """.formatted(supplierId, supplierInvoice, day, productId)))
                .andExpect(status().isCreated());
        MvcResult sale = mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId":%s,
                                  "invoiceDate":"%s",
                                  "paymentType":"CREDIT",
                                  "paidAmount":"0",
                                  "confirm":true,
                                  "items":[{
                                    "productId":%s,
                                    "quantity":"1",
                                    "sellingPrice":"80.0000",
                                    "discount":"0",
                                    "tax":"0"
                                  }]
                                }
                                """.formatted(customerId, day, productId)))
                .andExpect(status().isCreated())
                .andReturn();
        String invoice = JsonPath.read(sale.getResponse().getContentAsString(), "$.data.invoiceNumber");
        return new Books(customerId.longValue(), productCode, invoice, supplierInvoice);
    }

    private long customer(String token, String mark) throws Exception {
        Number customerId = idOf(mockMvc.perform(post("/api/v1/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerCode":"RK%s",
                                  "customerName":"Bulk customer %s",
                                  "phone":"9876500001",
                                  "openingBalance":"0",
                                  "creditLimit":"50000",
                                  "paymentTerms":"30",
                                  "status":"ACTIVE"
                                }
                                """.formatted(mark, mark)))
                .andExpect(status().isCreated())
                .andReturn());
        return customerId.longValue();
    }

    private static String suffix() {
        String mark = Long.toString(System.nanoTime(), 36).toUpperCase();
        return mark.substring(Math.max(0, mark.length() - 8));
    }

    private Number idOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String adminToken() throws Exception {
        String initial = login("admin", INITIAL_PASSWORD);
        if (initial == null) {
            return login("admin", REPLACEMENT_PASSWORD);
        }
        MvcResult result = mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + initial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """.formatted(INITIAL_PASSWORD, REPLACEMENT_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return readToken(result);
    }

    private String userToken(String username, String currentPassword, String newPassword) throws Exception {
        String initial = login(username, currentPassword);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + initial)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """.formatted(currentPassword, newPassword)))
                .andExpect(status().isOk())
                .andReturn();
        return readToken(result);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            return null;
        }
        return readToken(result);
    }

    private String readToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private record Books(long customerId, String productCode, String invoice, String supplierInvoice) {
    }
}
