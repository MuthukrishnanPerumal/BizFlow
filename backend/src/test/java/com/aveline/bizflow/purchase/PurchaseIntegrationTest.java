package com.aveline.bizflow.purchase;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PurchaseIntegrationTest {

    private static final String INITIAL_PASSWORD = "Aveline#Admin1";
    private static final String REPLACEMENT_PASSWORD = "Aveline#Admin2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.aveline.bizflow.identity.RoleRepository roleRepository;

    @Test
    void anonymousAndUnauthorizedUsersCannotPostPurchases() throws Exception {
        mockMvc.perform(get("/api/v1/purchases")).andExpect(status().isUnauthorized());

        String admin = adminToken();
        long roleId = roleRepository.findByCode("INVENTORY_USER").orElseThrow().getId();
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"stock.only",
                                  "fullName":"Stock Only",
                                  "email":"stock.only@aveline.local",
                                  "password":"Viewer#Pass1",
                                  "status":"ACTIVE",
                                  "roleIds":[%d]
                                }
                                """.formatted(roleId)))
                .andExpect(status().isCreated());
        String stock = userToken("stock.only", "Viewer#Pass1", "Viewer#Pass2");
        mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stock)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId":1,
                                  "invoiceNumber":"DENIED",
                                  "purchaseDate":"2026-10-02",
                                  "paymentType":"CASH",
                                  "confirm":true,
                                  "items":[{"productId":1,"quantity":"1","purchasePrice":"1","tax":"0"}]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsPurchaseWithStockSupplierBalanceAndPayment() throws Exception {
        String admin = adminToken();
        Fixture fixture = fixture(admin, "PICS");
        MvcResult created = mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseJson(fixture, "INV-1", "2", "1", "10", "1", "5", "0.50", "1.25", "-0.20", "10", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.purchaseNumber", startsWith("PUR-2026-")))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.subtotal").value(20.0))
                .andExpect(jsonPath("$.data.itemDiscount").value(1.0))
                .andExpect(jsonPath("$.data.totalDiscount").value(1.5))
                .andExpect(jsonPath("$.data.tax").value(0.95))
                .andExpect(jsonPath("$.data.grandTotal").value(20.5))
                .andExpect(jsonPath("$.data.paidAmount").value(10.0))
                .andExpect(jsonPath("$.data.balanceAmount").value(10.5))
                .andExpect(jsonPath("$.data.payments[0].amount").value(10.0))
                .andExpect(jsonPath("$.data.payments[0].status").value("POSTED"))
                .andExpect(jsonPath("$.data.stockImpact[0].transactionType").value("PURCHASE"))
                .andExpect(jsonPath("$.data.stockImpact[0].quantityIn").value(3.0))
                .andExpect(jsonPath("$.data.stockImpact[0].runningBalance").value(3.0))
                .andExpect(jsonPath("$.data.audits[0].eventType").value("PURCHASE_CREATED"))
                .andReturn();

        mockMvc.perform(get("/api/v1/suppliers/" + fixture.supplierId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.balance").value(10.5));
        mockMvc.perform(get("/api/v1/inventory/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("productId", String.valueOf(fixture.productId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quantityOnHand").value(3.0));

        mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseJson(fixture, "INV-1", "1", "0", "10", "0", "0", "0", "0", "0", "0", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("invoiceNumber"));

        mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseJson(fixture, "INV-2", "0", "0", "10", "0", "0", "0", "0", "0", "0", true)))
                .andExpect(status().isUnprocessableEntity());

        String body = created.getResponse().getContentAsString();
        mockMvc.perform(get("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("invoiceNumber", "INV-1")
                        .param("supplierId", String.valueOf(fixture.supplierId()))
                        .param("status", "CONFIRMED")
                        .param("dateFrom", "2026-10-01")
                        .param("dateTo", "2026-10-02")
                        .param("sort", "invoiceNumber,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].invoiceNumber").value("INV-1"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        JsonPath.read(body, "$.data.id");
    }

    @Test
    void updatesDraftAndConfirmedPurchases() throws Exception {
        String admin = adminToken();
        Fixture fixture = fixture(admin, "PICS");
        MvcResult draft = mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseJson(fixture, "INV-DRAFT", "2", "0", "10", "0", "0", "0", "0", "0", "0", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        String draftBody = draft.getResponse().getContentAsString();
        Number id = JsonPath.read(draftBody, "$.data.id");
        Number version = JsonPath.read(draftBody, "$.data.version");

        mockMvc.perform(get("/api/v1/inventory/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("productId", String.valueOf(fixture.productId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        MvcResult confirmed = mockMvc.perform(put("/api/v1/purchases/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(fixture, "INV-DRAFT", "4", "10", "0", version, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.grandTotal").value(40.0))
                .andExpect(jsonPath("$.data.stockImpact[0].runningBalance").value(4.0))
                .andReturn();
        Number confirmedVersion = JsonPath.read(confirmed.getResponse().getContentAsString(), "$.data.version");

        mockMvc.perform(put("/api/v1/purchases/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(fixture, "INV-DRAFT", "4", "8", "0", confirmedVersion, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grandTotal").value(32.0))
                .andExpect(jsonPath("$.data.stockImpact.length()").value(3));

        mockMvc.perform(get("/api/v1/suppliers/" + fixture.supplierId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.balance").value(32.0));
        mockMvc.perform(get("/api/v1/inventory/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("productId", String.valueOf(fixture.productId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quantityOnHand").value(4.0));
    }

    @Test
    void cancellationReversesStockAndSupplierBalance() throws Exception {
        String admin = adminToken();
        Fixture fixture = fixture(admin, "PICS");
        MvcResult created = mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseJson(fixture, "INV-CAN", "3", "0", "10", "0", "0", "0", "0", "0", "10", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.balanceAmount").value(20.0))
                .andReturn();
        Number id = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");
        Number version = JsonPath.read(created.getResponse().getContentAsString(), "$.data.version");

        mockMvc.perform(post("/api/v1/purchases/" + id + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.payments[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.stockImpact[?(@.transactionType == 'PURCHASE_REVERSAL')].quantityOut").value(3.0));

        mockMvc.perform(get("/api/v1/suppliers/" + fixture.supplierId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.balance").value(0.0));
        mockMvc.perform(get("/api/v1/inventory/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("productId", String.valueOf(fixture.productId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quantityOnHand").value(0.0));
    }

    @Test
    void partialAndCompleteReturnsStopAtAvailableQuantity() throws Exception {
        String admin = adminToken();
        Fixture fixture = fixture(admin, "PICS");
        MvcResult created = mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseJson(fixture, "INV-RET", "10", "0", "5", "0", "0", "0", "0", "0", "0", true)))
                .andExpect(status().isCreated())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        Number purchaseId = JsonPath.read(body, "$.data.id");
        Number version = JsonPath.read(body, "$.data.version");
        Number itemId = JsonPath.read(body, "$.data.items[0].id");

        MvcResult partial = mockMvc.perform(post("/api/v1/purchase-returns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnJson(purchaseId, version, itemId, "4")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.grandTotal").value(20.0))
                .andExpect(jsonPath("$.data.stockImpact[0].transactionType").value("PURCHASE_RETURN"))
                .andExpect(jsonPath("$.data.stockImpact[0].quantityOut").value(4.0))
                .andReturn();
        mockMvc.perform(get("/api/v1/purchases/" + purchaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_RETURNED"))
                .andExpect(jsonPath("$.data.balanceAmount").value(30.0))
                .andExpect(jsonPath("$.data.items[0].remainingQuantity").value(6.0));

        Number partialVersion = JsonPath.read(
                mockMvc.perform(get("/api/v1/purchases/" + purchaseId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                        .andReturn().getResponse().getContentAsString(),
                "$.data.version");
        mockMvc.perform(post("/api/v1/purchase-returns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnJson(purchaseId, partialVersion, itemId, "7")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RETURN_QUANTITY"));

        mockMvc.perform(post("/api/v1/purchase-returns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnJson(purchaseId, partialVersion, itemId, "6")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.grandTotal").value(30.0));
        mockMvc.perform(get("/api/v1/purchases/" + purchaseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"))
                .andExpect(jsonPath("$.data.balanceAmount").value(0.0));
        mockMvc.perform(get("/api/v1/suppliers/" + fixture.supplierId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.balance").value(0.0));
        mockMvc.perform(get("/api/v1/inventory/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("productId", String.valueOf(fixture.productId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quantityOnHand").value(0.0));

        Number returnedVersion = JsonPath.read(
                mockMvc.perform(get("/api/v1/purchases/" + purchaseId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                        .andReturn().getResponse().getContentAsString(),
                "$.data.version");
        mockMvc.perform(post("/api/v1/purchase-returns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnJson(purchaseId, returnedVersion, itemId, "1")))
                .andExpect(status().isUnprocessableEntity());
        partial.getResponse();
    }

    @Test
    void rejectsABatchPurchaseWithoutABatch() throws Exception {
        String admin = adminToken();
        Fixture fixture = fixture(admin, "BATCH");
        mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(purchaseJson(fixture, "INV-BATCH", "1", "0", "10", "0", "0", "0", "0", "0", "0", true)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].batch"));
    }

    private Fixture fixture(String token, String stockType) throws Exception {
        Number categoryId = idOf(mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BUY" + stockType + "\",\"name\":\"Buy " + stockType + "\",\"description\":\"Purchases\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        Number brandId = idOf(mockMvc.perform(post("/api/v1/brands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BR" + stockType + "\",\"name\":\"Brand " + stockType + "\",\"description\":\"House\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        Number unitId = unitId(token, "PCS");
        boolean batch = "BATCH".equals(stockType);
        MvcResult product = mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode":"PUR-%s",
                                  "barcode":"890%s",
                                  "name":"Purchase item",
                                  "shortName":"PURITEM",
                                  "description":"Bought goods",
                                  "categoryId":%s,
                                  "brandId":%s,
                                  "unitId":%s,
                                  "taxPercent":"0.0000",
                                  "purchasePrice":"10.0000",
                                  "sellingPrice":"80.0000",
                                  "mrp":"100.0000",
                                  "minimumSellingPrice":"70.0000",
                                  "reorderLevel":"0",
                                  "stockType":"%s",
                                  "batchTracking":%s,
                                  "serialTracking":false,
                                  "expiryTracking":false,
                                  "status":"ACTIVE"
                                }
                                """.formatted(stockType, stockType, categoryId, brandId, unitId, stockType, batch)))
                .andExpect(status().isCreated())
                .andReturn();
        Number supplierId = idOf(mockMvc.perform(post("/api/v1/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierCode":"SUP%s",
                                  "supplierName":"Northwind %s",
                                  "phone":"9876543210",
                                  "openingBalance":"0",
                                  "creditLimit":"5000",
                                  "status":"ACTIVE"
                                }
                                """.formatted(stockType, stockType)))
                .andExpect(status().isCreated())
                .andReturn());
        return new Fixture(supplierId.longValue(), JsonPath.read(product.getResponse().getContentAsString(), "$.data.id"));
    }

    private String purchaseJson(
            Fixture fixture,
            String invoice,
            String quantity,
            String free,
            String price,
            String discount,
            String tax,
            String billDiscount,
            String charges,
            String roundOff,
            String paid,
            boolean confirm) {
        return """
                {
                  "supplierId":%d,
                  "invoiceNumber":"%s",
                  "purchaseDate":"2026-10-02",
                  "paymentType":"CASH",
                  "referenceNumber":"PO-1",
                  "notes":"October intake",
                  "billDiscount":"%s",
                  "additionalCharges":"%s",
                  "roundOff":"%s",
                  "paidAmount":"%s",
                  "confirm":%s,
                  "items":[{
                    "productId":%s,
                    "quantity":"%s",
                    "freeQuantity":"%s",
                    "purchasePrice":"%s",
                    "discount":"%s",
                    "tax":"%s"
                  }]
                }
                """.formatted(
                fixture.supplierId(),
                invoice,
                billDiscount,
                charges,
                roundOff,
                paid,
                confirm,
                fixture.productId(),
                quantity,
                free,
                price,
                discount,
                tax);
    }

    private String updateJson(Fixture fixture, String invoice, String quantity, String price, String paid, Number version, boolean confirm) {
        return """
                {
                  "supplierId":%d,
                  "invoiceNumber":"%s",
                  "purchaseDate":"2026-10-02",
                  "paymentType":"CREDIT",
                  "billDiscount":"0",
                  "additionalCharges":"0",
                  "roundOff":"0",
                  "paidAmount":"%s",
                  "confirm":%s,
                  "version":%s,
                  "items":[{
                    "productId":%s,
                    "quantity":"%s",
                    "freeQuantity":"0",
                    "purchasePrice":"%s",
                    "discount":"0",
                    "tax":"0"
                  }]
                }
                """.formatted(fixture.supplierId(), invoice, paid, confirm, version, fixture.productId(), quantity, price);
    }

    private String returnJson(Number purchaseId, Number version, Number itemId, String quantity) {
        return """
                {
                  "purchaseId":%s,
                  "purchaseVersion":%s,
                  "returnDate":"2026-10-02",
                  "notes":"Damaged",
                  "items":[{"purchaseItemId":%s,"quantity":"%s","freeQuantity":"0"}]
                }
                """.formatted(purchaseId, version, itemId, quantity);
    }

    private Number unitId(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/units")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("search", name))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.items[0].id");
    }

    private Number idOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String adminToken() throws Exception {
        String initial = login("admin", INITIAL_PASSWORD);
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

    private String readToken(MvcResult result) throws Exception {
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.data.accessToken", String.class);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return readToken(result);
    }

    private record Fixture(long supplierId, Number productId) {
    }
}
