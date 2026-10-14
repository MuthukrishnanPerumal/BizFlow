package com.aveline.bizflow.inventory;

import com.aveline.bizflow.audit.AuditEventRepository;
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

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryIntegrationTest {

    private static final String INITIAL_PASSWORD = "Aveline#Admin1";
    private static final String REPLACEMENT_PASSWORD = "Aveline#Admin2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.aveline.bizflow.identity.RoleRepository roleRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void anonymousUsersCannotReadStock() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void salesUserCannotAdjustOrTransfer() throws Exception {
        String admin = adminToken();
        try {
            long roleId = roleRepository.findByCode("SALES_USER").orElseThrow().getId();
            String mark = mark();
            mockMvc.perform(post("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username":"sales.%s",
                                      "fullName":"Sales Only",
                                      "email":"sales.%s@aveline.local",
                                      "password":"Viewer#Pass1",
                                      "status":"ACTIVE",
                                      "roleIds":[%d]
                                    }
                                    """.formatted(mark, mark, roleId)))
                    .andExpect(status().isCreated());
            String sales = userToken("sales." + mark, "Viewer#Pass1", "Viewer#Pass2");
            mockMvc.perform(post("/api/v1/inventory/adjustments")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + sales)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(adjustment(1, null, "INCREASE", "1", "OPENING_STOCK")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/inventory/transfers")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + sales)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "fromLocationId":1,
                                      "toLocationId":2,
                                      "transferDate":"2026-10-02",
                                      "reason":"Move",
                                      "complete":false,
                                      "lines":[{"productId":1,"quantity":"1"}]
                                    }
                                    """))
                    .andExpect(status().isForbidden());
        } finally {
            restoreAdminPassword();
        }
    }

    @Test
    void adjustmentUpdatesStockLedgerBalanceAndAudit() throws Exception {
        String admin = adminToken();
        try {
            long locationId = locationId(admin, "MAIN");
            long productId = product(admin, "0.0000", null);
            mockMvc.perform(post("/api/v1/inventory/adjustments")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(adjustment(productId, locationId, "INCREASE", "5", "OPENING_STOCK")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.adjustmentNumber", startsWith("ADJ-2026-")));
            MvcResult decreased = mockMvc.perform(post("/api/v1/inventory/adjustments")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(adjustment(productId, locationId, "DECREASE", "2", "COUNT_CORRECTION")))
                    .andExpect(status().isCreated())
                    .andReturn();
            Number adjustmentId = JsonPath.read(decreased.getResponse().getContentAsString(), "$.data.id");
            mockMvc.perform(get("/api/v1/inventory/ledger")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .param("productId", String.valueOf(productId))
                            .param("sort", "date,asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].transactionType").value("OPENING_STOCK"))
                    .andExpect(jsonPath("$.data.items[0].quantityIn").value(5.0))
                    .andExpect(jsonPath("$.data.items[0].runningBalance").value(5.0))
                    .andExpect(jsonPath("$.data.items[1].transactionType").value("STOCK_ADJUSTMENT"))
                    .andExpect(jsonPath("$.data.items[1].quantityOut").value(2.0))
                    .andExpect(jsonPath("$.data.items[1].runningBalance").value(3.0))
                    .andExpect(jsonPath("$.data.items[1].user").value("admin"));
            mockMvc.perform(get("/api/v1/inventory/overview")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .param("search", codeOf(productId, admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].currentStock").value(3.0))
                    .andExpect(jsonPath("$.data.items[0].status").value("IN_STOCK"));
            mockMvc.perform(post("/api/v1/inventory/adjustments")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(adjustment(productId, locationId, "DECREASE", "9", "DAMAGE")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].code").value("INSUFFICIENT_STOCK"));
            mockMvc.perform(get("/api/v1/inventory/balances")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .param("productId", String.valueOf(productId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].quantityOnHand").value(3.0))
                    .andExpect(jsonPath("$.data[0].availableQuantity").value(3.0));
            org.junit.jupiter.api.Assertions.assertFalse(
                    auditEventRepository.findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
                            "STOCK_ADJUSTMENT", String.valueOf(adjustmentId)).isEmpty());
        } finally {
            restoreAdminPassword();
        }
    }

    @Test
    void lowOutAndOverStockScreensFilterByStatus() throws Exception {
        String admin = adminToken();
        try {
            long locationId = locationId(admin, "MAIN");
            long lowId = product(admin, "5.0000", null);
            long outId = product(admin, "1.0000", null);
            long overId = product(admin, "0.0000", "1.0000");
            mockMvc.perform(post("/api/v1/inventory/adjustments")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(adjustment(lowId, locationId, "INCREASE", "2", "OPENING_STOCK")))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/api/v1/inventory/adjustments")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(adjustment(overId, locationId, "INCREASE", "4", "FOUND")))
                    .andExpect(status().isCreated());
            String lowCode = codeOf(lowId, admin);
            String outCode = codeOf(outId, admin);
            String overCode = codeOf(overId, admin);
            mockMvc.perform(get("/api/v1/inventory/low-stock")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .param("search", lowCode))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].productId").value(lowId))
                    .andExpect(jsonPath("$.data.items[0].status").value("LOW_STOCK"))
                    .andExpect(jsonPath("$.data.items[0].shortageQuantity").value(3.0));
            mockMvc.perform(get("/api/v1/inventory/out-of-stock")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .param("search", outCode))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].productId").value(outId))
                    .andExpect(jsonPath("$.data.items[0].status").value("OUT_OF_STOCK"))
                    .andExpect(jsonPath("$.data.items[0].currentStock").value(0.0));
            mockMvc.perform(get("/api/v1/inventory/overview")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .param("search", overCode))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].currentStock").value(4.0))
                    .andExpect(jsonPath("$.data.items[0].maximumStockLevel").value(1.0))
                    .andExpect(jsonPath("$.data.items[0].status").value("OVER_STOCK"));
            mockMvc.perform(get("/api/v1/inventory/dashboard")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.valuationMethod").value("AVERAGE"))
                    .andExpect(jsonPath("$.data.lowStock").isNumber());
        } finally {
            restoreAdminPassword();
        }
    }

    @Test
    void transferMovesStockAndRejectsInvalidMovements() throws Exception {
        String admin = adminToken();
        try {
            long main = locationId(admin, "MAIN");
            long store = locationId(admin, "STORE");
            long productId = product(admin, "0.0000", null);
            mockMvc.perform(post("/api/v1/inventory/adjustments")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(adjustment(productId, main, "INCREASE", "5", "OPENING_STOCK")))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/api/v1/inventory/transfers")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transfer(main, main, productId, "1", true)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].code").value("SAME_LOCATION"));
            mockMvc.perform(post("/api/v1/inventory/transfers")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transfer(main, store, productId, "9", true)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].code").value("INSUFFICIENT_STOCK"));
            MvcResult draft = mockMvc.perform(post("/api/v1/inventory/transfers")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transfer(main, store, productId, "2", false)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"))
                    .andReturn();
            Number transferId = JsonPath.read(draft.getResponse().getContentAsString(), "$.data.id");
            Number version = JsonPath.read(draft.getResponse().getContentAsString(), "$.data.version");
            mockMvc.perform(post("/api/v1/inventory/transfers/" + transferId + "/confirm")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"version\":" + version + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
            mockMvc.perform(get("/api/v1/inventory/balances")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .param("productId", String.valueOf(productId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.locationCode == 'MAIN')].reservedQuantity").value(hasItem(2.0)))
                    .andExpect(jsonPath("$.data[?(@.locationCode == 'MAIN')].availableQuantity").value(hasItem(3.0)));
            Number confirmedVersion = JsonPath.read(
                    mockMvc.perform(get("/api/v1/inventory/transfers/" + transferId)
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                            .andExpect(status().isOk())
                            .andReturn().getResponse().getContentAsString(),
                    "$.data.version");
            mockMvc.perform(post("/api/v1/inventory/transfers/" + transferId + "/complete")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"version\":" + confirmedVersion + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));
            mockMvc.perform(get("/api/v1/inventory/ledger")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .param("productId", String.valueOf(productId))
                            .param("transactionType", "STOCK_TRANSFER_IN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].quantityIn").value(2.0))
                    .andExpect(jsonPath("$.data.items[0].runningBalance").value(2.0));
            mockMvc.perform(get("/api/v1/inventory/valuation")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .param("search", codeOf(productId, admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.summary.method").value("AVERAGE"))
                    .andExpect(jsonPath("$.data.items[0].officialValue").isNumber());
        } finally {
            restoreAdminPassword();
        }
    }

    @Test
    void salesReturnRestoresStockThroughTheLedger() throws Exception {
        String admin = adminToken();
        try {
            long productId = product(admin, "0.0000", null);
            long supplierId = party(admin, "suppliers", "supplier", productMark(productId));
            long customerId = party(admin, "customers", "customer", productMark(productId));
            mockMvc.perform(post("/api/v1/purchases")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "supplierId":%d,
                                      "invoiceNumber":"PIN-%s",
                                      "purchaseDate":"2026-10-02",
                                      "paymentType":"CASH",
                                      "paidAmount":"0",
                                      "confirm":true,
                                      "items":[{
                                        "productId":%d,
                                        "quantity":"6",
                                        "freeQuantity":"0",
                                        "purchasePrice":"10.0000",
                                        "discount":"0",
                                        "tax":"0"
                                      }]
                                    }
                                    """.formatted(supplierId, productMark(productId), productId)))
                    .andExpect(status().isCreated());
            MvcResult sale = mockMvc.perform(post("/api/v1/sales")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "customerId":%d,
                                      "invoiceDate":"2026-10-02",
                                      "paymentType":"CREDIT",
                                      "orderDiscount":"0",
                                      "additionalCharges":"0",
                                      "roundOff":"0",
                                      "paidAmount":"0",
                                      "confirm":true,
                                      "items":[{"productId":%d,"quantity":"4","sellingPrice":"20.0000","discount":"0","tax":"0"}]
                                    }
                                    """.formatted(customerId, productId)))
                    .andExpect(status().isCreated())
                    .andReturn();
            String saleBody = sale.getResponse().getContentAsString();
            Number saleId = JsonPath.read(saleBody, "$.data.id");
            Number version = JsonPath.read(saleBody, "$.data.version");
            Number itemId = JsonPath.read(saleBody, "$.data.items[0].id");
            mockMvc.perform(post("/api/v1/sales-returns")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "saleId":%s,
                                      "saleVersion":%s,
                                      "returnDate":"2026-10-02",
                                      "reason":"Customer changed their mind",
                                      "refundMethod":"CREDIT",
                                      "items":[{"saleItemId":%s,"quantity":"4"}]
                                    }
                                    """.formatted(saleId, version, itemId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.stockImpact[0].transactionType").value("SALES_RETURN"));
            mockMvc.perform(get("/api/v1/inventory/ledger")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .param("productId", String.valueOf(productId))
                            .param("sort", "date,asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[*].transactionType", hasItem("PURCHASE")))
                    .andExpect(jsonPath("$.data.items[*].transactionType", hasItem("SALE")))
                    .andExpect(jsonPath("$.data.items[*].transactionType", hasItem("SALES_RETURN")));
            mockMvc.perform(get("/api/v1/inventory/balances")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .param("productId", String.valueOf(productId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].quantityOnHand").value(6.0));
        } finally {
            restoreAdminPassword();
        }
    }

    @Test
    void fifoSaleConsumesTheOldestCostLayer() throws Exception {
        String admin = adminToken();
        try {
            jdbcTemplate.update(
                    "UPDATE platform_setting SET setting_value = 'FIFO' WHERE setting_key = 'inventory.valuation-method'");
            long productId = product(admin, "0.0000", null);
            long supplierId = party(admin, "suppliers", "supplier", productMark(productId));
            long customerId = party(admin, "customers", "customer", productMark(productId));
            purchase(admin, supplierId, productId, "1", "10.0000", "A");
            purchase(admin, supplierId, productId, "1", "30.0000", "B");
            mockMvc.perform(post("/api/v1/sales")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "customerId":%d,
                                      "invoiceDate":"2026-10-02",
                                      "paymentType":"CREDIT",
                                      "orderDiscount":"0",
                                      "additionalCharges":"0",
                                      "roundOff":"0",
                                      "paidAmount":"0",
                                      "confirm":true,
                                      "items":[{"productId":%d,"quantity":"1","sellingPrice":"20.0000","discount":"0","tax":"0"}]
                                    }
                                    """.formatted(customerId, productId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.stockImpact[0].unitCost").value(10.0));
        } finally {
            jdbcTemplate.update(
                    "UPDATE platform_setting SET setting_value = 'AVERAGE' WHERE setting_key = 'inventory.valuation-method'");
            restoreAdminPassword();
        }
    }

    private void purchase(String token, long supplierId, long productId, String quantity, String cost, String suffix) throws Exception {
        mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId":%d,
                                  "invoiceNumber":"PIN-%s-%s",
                                  "purchaseDate":"2026-10-02",
                                  "paymentType":"CASH",
                                  "paidAmount":"0",
                                  "confirm":true,
                                  "items":[{
                                    "productId":%d,
                                    "quantity":"%s",
                                    "freeQuantity":"0",
                                    "purchasePrice":"%s",
                                    "discount":"0",
                                    "tax":"0"
                                  }]
                                }
                                """.formatted(supplierId, productMark(productId), suffix, productId, quantity, cost)))
                .andExpect(status().isCreated());
    }

    private long product(String token, String reorder, String maximum) throws Exception {
        String mark = mark();
        Number categoryId = idOf(mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"C" + mark + "\",\"name\":\"Cat " + mark + "\",\"description\":\"Stock\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        Number brandId = idOf(mockMvc.perform(post("/api/v1/brands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"B" + mark + "\",\"name\":\"Brand " + mark + "\",\"description\":\"House\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        Number unitId = JsonPath.read(mockMvc.perform(get("/api/v1/units")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("search", "PCS"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.items[0].id");
        String maximumField = maximum == null ? "" : "\"maximumStockLevel\":\"" + maximum + "\",";
        MvcResult created = mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode":"INV-%s",
                                  "barcode":"BC%s",
                                  "name":"Stock item %s",
                                  "shortName":"STK",
                                  "description":"Tracked goods",
                                  "categoryId":%s,
                                  "brandId":%s,
                                  "unitId":%s,
                                  "taxPercent":"0.0000",
                                  "purchasePrice":"10.0000",
                                  "sellingPrice":"25.0000",
                                  "mrp":"30.0000",
                                  "minimumSellingPrice":"0.0000",
                                  "reorderLevel":"%s",
                                  %s
                                  "stockType":"PICS",
                                  "batchTracking":false,
                                  "serialTracking":false,
                                  "expiryTracking":false,
                                  "status":"ACTIVE"
                                }
                                """.formatted(mark, mark, mark, categoryId, brandId, unitId, reorder, maximumField)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.data.id")).longValue();
    }

    private long party(String token, String path, String label, String mark) throws Exception {
        String code = ("suppliers".equals(path) ? "S" : "U") + mark;
        String nameField = "suppliers".equals(path) ? "supplierName" : "customerName";
        String codeField = "suppliers".equals(path) ? "supplierCode" : "customerCode";
        MvcResult created = mockMvc.perform(post("/api/v1/" + path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "%s":"%s",
                                  "%s":"%s %s",
                                  "phone":"9876543210",
                                  "openingBalance":"0",
                                  "creditLimit":"5000",
                                  "status":"ACTIVE"
                                }
                                """.formatted(codeField, code, nameField, label, mark)))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) idOf(created)).longValue();
    }

    private String productMark(long productId) {
        return "P" + productId;
    }

    private long locationId(String token, String code) throws Exception {
        String body = mockMvc.perform(get("/api/v1/inventory/locations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$.data[?(@.code == '" + code + "')].id");
        return ids.get(0).longValue();
    }

    private String codeOf(long productId, String token) throws Exception {
        return JsonPath.read(mockMvc.perform(get("/api/v1/products/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.productCode");
    }

    private String adjustment(long productId, Long locationId, String direction, String quantity, String reason) {
        String location = locationId == null ? "" : "\"locationId\":" + locationId + ",";
        return """
                {
                  "productId":%d,
                  %s
                  "direction":"%s",
                  "quantity":"%s",
                  "reason":"%s",
                  "referenceNumber":"REF-1",
                  "adjustmentDate":"2026-10-02",
                  "notes":"Count"
                }
                """.formatted(productId, location, direction, quantity, reason);
    }

    private String transfer(long from, long to, long productId, String quantity, boolean complete) {
        return """
                {
                  "fromLocationId":%d,
                  "toLocationId":%d,
                  "transferDate":"2026-10-02",
                  "reason":"Rebalance",
                  "notes":"Evening move",
                  "complete":%s,
                  "lines":[{"productId":%d,"quantity":"%s"}]
                }
                """.formatted(from, to, complete, productId, quantity);
    }

    private String mark() {
        String value = Long.toUnsignedString(System.nanoTime(), 36).toUpperCase();
        return value.length() > 10 ? value.substring(value.length() - 10) : value;
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
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private void restoreAdminPassword() throws Exception {
        String current = login("admin", REPLACEMENT_PASSWORD);
        if (current == null) {
            return;
        }
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + current)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"%s"}
                                """.formatted(REPLACEMENT_PASSWORD, INITIAL_PASSWORD)))
                .andExpect(status().isOk());
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
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            return null;
        }
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
