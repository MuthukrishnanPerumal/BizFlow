package com.aveline.bizflow.sale;

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
import org.springframework.transaction.annotation.Propagation;
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
class SaleIntegrationTest {

    private static final String INITIAL_PASSWORD = "Aveline#Admin1";
    private static final String REPLACEMENT_PASSWORD = "Aveline#Admin2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.aveline.bizflow.identity.RoleRepository roleRepository;

    @Test
    void anonymousAndUnauthorizedUsersCannotPostSales() throws Exception {
        mockMvc.perform(get("/api/v1/sales")).andExpect(status().isUnauthorized());

        String admin = adminToken();
        long roleId = roleRepository.findByCode("INVENTORY_USER").orElseThrow().getId();
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"stock.sales",
                                  "fullName":"Stock Sales",
                                  "email":"stock.sales@aveline.local",
                                  "password":"Viewer#Pass1",
                                  "status":"ACTIVE",
                                  "roleIds":[%d]
                                }
                                """.formatted(roleId)))
                .andExpect(status().isCreated());
        String stock = userToken("stock.sales", "Viewer#Pass1", "Viewer#Pass2");
        mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stock)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId":1,
                                  "invoiceDate":"2026-10-02",
                                  "paymentType":"CASH",
                                  "confirm":true,
                                  "items":[{"productId":1,"quantity":"1","sellingPrice":"1","tax":"0"}]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsSaleWithStockCustomerBalanceAndPayment() throws Exception {
        String admin = adminToken();
        Fixture fixture = stocked(admin, "PICS", "5", "10");
        MvcResult created = mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(fixture, "2", "80", "0", "0", "0", "0", "0", "160", "CASH", true, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.invoiceNumber", startsWith("SAL-2026-")))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.subtotal").value(160.0))
                .andExpect(jsonPath("$.data.grandTotal").value(160.0))
                .andExpect(jsonPath("$.data.paidAmount").value(160.0))
                .andExpect(jsonPath("$.data.balanceAmount").value(0.0))
                .andExpect(jsonPath("$.data.payments[0].amount").value(160.0))
                .andExpect(jsonPath("$.data.payments[0].status").value("POSTED"))
                .andExpect(jsonPath("$.data.stockImpact[0].transactionType").value("SALE"))
                .andExpect(jsonPath("$.data.stockImpact[0].quantityOut").value(2.0))
                .andExpect(jsonPath("$.data.stockImpact[0].runningBalance").value(3.0))
                .andExpect(jsonPath("$.data.audits[0].eventType").value("SALE_CREATED"))
                .andReturn();

        mockMvc.perform(get("/api/v1/customers/" + fixture.customerId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.balance").value(0.0));
        mockMvc.perform(get("/api/v1/inventory/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("productId", String.valueOf(fixture.productId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quantityOnHand").value(3.0));

        String body = created.getResponse().getContentAsString();
        String invoice = JsonPath.read(body, "$.data.invoiceNumber");
        mockMvc.perform(get("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("invoiceNumber", invoice)
                        .param("customerId", String.valueOf(fixture.customerId()))
                        .param("status", "COMPLETED")
                        .param("paymentType", "CASH")
                        .param("dateFrom", "2026-10-01")
                        .param("dateTo", "2026-10-02")
                        .param("sort", "saleNumber,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].invoiceNumber").value(invoice))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/v1/products/lookup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("barcode", fixture.barcode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(fixture.productId().intValue()));
    }

    @Test
    void creditSaleUpdatesCustomerBalanceAndMixedPaymentsSplitReceipts() throws Exception {
        String admin = adminToken();
        Fixture fixture = stocked(admin, "PICS", "4", "10");
        mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(fixture, "1", "80", "0", "0", "0", "0", "0", "0", "CREDIT", true, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.balanceAmount").value(80.0));
        mockMvc.perform(get("/api/v1/customers/" + fixture.customerId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.balance").value(80.0));

        mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(fixture, "1", "80", "0", "0", "0", "0", "0", "80", "MIXED", true,
                                "[{\"method\":\"CASH\",\"amount\":\"30\"},{\"method\":\"UPI\",\"amount\":\"50\",\"referenceNumber\":\"UPI1\"}]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.payments.length()").value(2))
                .andExpect(jsonPath("$.data.balanceAmount").value(0.0));
        mockMvc.perform(get("/api/v1/customers/" + fixture.customerId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.balance").value(80.0));
    }

    @Test
    void updatesDraftAndConfirmedSales() throws Exception {
        String admin = adminToken();
        Fixture fixture = stocked(admin, "PICS", "6", "10");
        MvcResult draft = mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(fixture, "2", "80", "0", "0", "0", "0", "0", "0", "CREDIT", false, null)))
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
                .andExpect(jsonPath("$.data[0].quantityOnHand").value(6.0));

        MvcResult confirmed = mockMvc.perform(put("/api/v1/sales/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(fixture, "3", "80", version, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.grandTotal").value(240.0))
                .andExpect(jsonPath("$.data.stockImpact[0].runningBalance").value(3.0))
                .andReturn();
        Number confirmedVersion = JsonPath.read(confirmed.getResponse().getContentAsString(), "$.data.version");
        mockMvc.perform(put("/api/v1/sales/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(fixture, "2", "80", confirmedVersion, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grandTotal").value(160.0));
        mockMvc.perform(get("/api/v1/inventory/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("productId", String.valueOf(fixture.productId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quantityOnHand").value(4.0));
        mockMvc.perform(get("/api/v1/customers/" + fixture.customerId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.balance").value(160.0));
    }

    @Test
    void cancellationReversesStockAndCustomerBalance() throws Exception {
        String admin = adminToken();
        Fixture fixture = stocked(admin, "PICS", "3", "10");
        MvcResult created = mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(fixture, "2", "80", "0", "0", "0", "0", "0", "100", "CASH", true, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.balanceAmount").value(60.0))
                .andReturn();
        Number id = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");
        Number version = JsonPath.read(created.getResponse().getContentAsString(), "$.data.version");
        mockMvc.perform(post("/api/v1/sales/" + id + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.payments[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.stockImpact[?(@.transactionType == 'SALE_REVERSAL')].quantityIn").value(2.0));
        mockMvc.perform(get("/api/v1/customers/" + fixture.customerId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.balance").value(0.0));
        mockMvc.perform(get("/api/v1/inventory/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("productId", String.valueOf(fixture.productId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quantityOnHand").value(3.0));
    }

    @Test
    void salesUserCannotCancel() throws Exception {
        String admin = adminToken();
        long roleId = roleRepository.findByCode("SALES_USER").orElseThrow().getId();
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"counter.user",
                                  "fullName":"Counter User",
                                  "email":"counter.user@aveline.local",
                                  "password":"Seller#Pass1",
                                  "status":"ACTIVE",
                                  "roleIds":[%d]
                                }
                                """.formatted(roleId)))
                .andExpect(status().isCreated());
        Fixture fixture = stocked(admin, "PICS", "2", "10");
        MvcResult created = mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(fixture, "1", "80", "0", "0", "0", "0", "0", "0", "CREDIT", true, null)))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");
        Number version = JsonPath.read(created.getResponse().getContentAsString(), "$.data.version");
        String seller = userToken("counter.user", "Seller#Pass1", "Seller#Pass2");
        mockMvc.perform(post("/api/v1/sales/" + id + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInsufficientStockInactiveProductsAndMissingBatches() throws Exception {
        String admin = adminToken();
        Fixture fixture = stocked(admin, "PICS", "2", "10");
        mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(fixture, "3", "80", "0", "0", "0", "0", "0", "0", "CREDIT", true, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INSUFFICIENT_STOCK"));
        mockMvc.perform(get("/api/v1/inventory/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("productId", String.valueOf(fixture.productId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quantityOnHand").value(2.0));

        Fixture inactive = catalog(admin, "PICS", "OFF", "INACTIVE");
        mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(new Fixture(fixture.customerId(), inactive.productId(), inactive.barcode(), fixture.supplierId()),
                                "1", "80", "0", "0", "0", "0", "0", "0", "CREDIT", true, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("PRODUCT_INACTIVE"));

        Fixture batch = stocked(admin, "BATCH", "2", "10");
        mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(batch, "1", "80", "0", "0", "0", "0", "0", "0", "CREDIT", true, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("items[0].batch"));
    }

    @Test
    void partialAndFullReturnsRestoreStockAndCustomerBalance() throws Exception {
        String admin = adminToken();
        Fixture fixture = stocked(admin, "PICS", "10", "10");
        MvcResult created = mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saleJson(fixture, "10", "80", "0", "0", "0", "0", "0", "0", "CREDIT", true, null)))
                .andExpect(status().isCreated())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        Number saleId = JsonPath.read(body, "$.data.id");
        Number version = JsonPath.read(body, "$.data.version");
        Number itemId = JsonPath.read(body, "$.data.items[0].id");

        mockMvc.perform(post("/api/v1/sales-returns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnJson(saleId, version, itemId, "4")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.returnNumber", startsWith("SRT-2026-")))
                .andExpect(jsonPath("$.data.grandTotal").value(320.0))
                .andExpect(jsonPath("$.data.stockImpact[0].transactionType").value("SALES_RETURN"))
                .andExpect(jsonPath("$.data.stockImpact[0].quantityIn").value(4.0));
        mockMvc.perform(get("/api/v1/sales/" + saleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_RETURNED"))
                .andExpect(jsonPath("$.data.balanceAmount").value(480.0))
                .andExpect(jsonPath("$.data.items[0].remainingQuantity").value(6.0));

        Number partialVersion = JsonPath.read(
                mockMvc.perform(get("/api/v1/sales/" + saleId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                        .andReturn().getResponse().getContentAsString(),
                "$.data.version");
        mockMvc.perform(post("/api/v1/sales-returns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnJson(saleId, partialVersion, itemId, "7")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("RETURN_QUANTITY"));
        mockMvc.perform(post("/api/v1/sales-returns")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnJson(saleId, partialVersion, itemId, "6")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.grandTotal").value(480.0));
        mockMvc.perform(get("/api/v1/sales/" + saleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"))
                .andExpect(jsonPath("$.data.balanceAmount").value(0.0));
        mockMvc.perform(get("/api/v1/customers/" + fixture.customerId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.balance").value(0.0));
        mockMvc.perform(get("/api/v1/inventory/balances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("productId", String.valueOf(fixture.productId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quantityOnHand").value(10.0));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void creditLimitFailureRollsBackInventory() throws Exception {
        String admin = adminToken();
        try {
            Fixture fixture = stocked(admin, "PICS", "4", "10", "1");
            mockMvc.perform(post("/api/v1/sales")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(saleJson(fixture, "2", "80", "0", "0", "0", "0", "0", "0", "CREDIT", true, null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].code").value("CREDIT_LIMIT"));
            mockMvc.perform(get("/api/v1/inventory/balances")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .param("productId", String.valueOf(fixture.productId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].quantityOnHand").value(4.0));
            mockMvc.perform(get("/api/v1/customers/" + fixture.customerId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.account.balance").value(0.0));
        } finally {
            restoreAdminPassword();
        }
    }

    private Fixture stocked(String token, String stockType, String quantity, String cost) throws Exception {
        return stocked(token, stockType, quantity, cost, "5000");
    }

    private Fixture stocked(String token, String stockType, String quantity, String cost, String creditLimit) throws Exception {
        String mark = stockType + System.nanoTime();
        Fixture catalog = catalog(token, stockType, mark, "ACTIVE");
        Number supplierId = idOf(mockMvc.perform(post("/api/v1/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierCode":"SS%s",
                                  "supplierName":"Sale supplier %s",
                                  "phone":"9876543210",
                                  "openingBalance":"0",
                                  "creditLimit":"5000",
                                  "status":"ACTIVE"
                                }
                                """.formatted(mark.substring(Math.max(0, mark.length() - 8)), mark)))
                .andExpect(status().isCreated())
                .andReturn());
        Number customerId = idOf(mockMvc.perform(post("/api/v1/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerCode":"SC%s",
                                  "customerName":"Sale customer %s",
                                  "phone":"9876500000",
                                  "openingBalance":"0",
                                  "creditLimit":"%s",
                                  "status":"ACTIVE"
                                }
                                """.formatted(mark.substring(Math.max(0, mark.length() - 8)), mark, creditLimit)))
                .andExpect(status().isCreated())
                .andReturn());
        String batchField = "BATCH".equals(stockType) ? "\"batch\":\"LOT1\"," : "";
        mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId":%s,
                                  "invoiceNumber":"PIN-%s",
                                  "purchaseDate":"2026-10-02",
                                  "paymentType":"CASH",
                                  "paidAmount":"0",
                                  "confirm":true,
                                  "items":[{
                                    "productId":%s,
                                    %s
                                    "quantity":"%s",
                                    "freeQuantity":"0",
                                    "purchasePrice":"%s",
                                    "discount":"0",
                                    "tax":"0"
                                  }]
                                }
                                """.formatted(supplierId, mark, catalog.productId(), batchField, quantity, cost)))
                .andExpect(status().isCreated());
        return new Fixture(customerId.longValue(), catalog.productId(), catalog.barcode(), supplierId.longValue());
    }

    private Fixture catalog(String token, String stockType, String mark, String status) throws Exception {
        Number categoryId = idOf(mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SC" + mark + "\",\"name\":\"Sale " + mark + "\",\"description\":\"Sales\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        Number brandId = idOf(mockMvc.perform(post("/api/v1/brands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"SB" + mark + "\",\"name\":\"Brand " + mark + "\",\"description\":\"House\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        Number unitId = unitId(token, "PCS");
        boolean batch = "BATCH".equals(stockType);
        String barcode = "89" + Math.abs(mark.hashCode());
        MvcResult product = mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode":"SAL-%s",
                                  "barcode":"%s",
                                  "name":"Sale item %s",
                                  "shortName":"SITEM",
                                  "description":"Sold goods",
                                  "categoryId":%s,
                                  "brandId":%s,
                                  "unitId":%s,
                                  "taxPercent":"0.0000",
                                  "purchasePrice":"10.0000",
                                  "sellingPrice":"80.0000",
                                  "mrp":"100.0000",
                                  "minimumSellingPrice":"0.0000",
                                  "reorderLevel":"0",
                                  "stockType":"%s",
                                  "batchTracking":%s,
                                  "serialTracking":false,
                                  "expiryTracking":false,
                                  "status":"%s"
                                }
                                """.formatted(mark, barcode, mark, categoryId, brandId, unitId, stockType, batch, status)))
                .andExpect(status().isCreated())
                .andReturn();
        return new Fixture(0, JsonPath.read(product.getResponse().getContentAsString(), "$.data.id"), barcode, 0);
    }

    private String saleJson(
            Fixture fixture,
            String quantity,
            String price,
            String discount,
            String tax,
            String orderDiscount,
            String charges,
            String roundOff,
            String paid,
            String paymentType,
            boolean confirm,
            String tenders) {
        return """
                {
                  "customerId":%d,
                  "invoiceDate":"2026-10-02",
                  "paymentType":"%s",
                  "referenceNumber":"POS-1",
                  "notes":"Counter sale",
                  "orderDiscount":"%s",
                  "additionalCharges":"%s",
                  "roundOff":"%s",
                  "paidAmount":"%s",
                  "confirm":%s,
                  "items":[{
                    "productId":%s,
                    "quantity":"%s",
                    "sellingPrice":"%s",
                    "discount":"%s",
                    "tax":"%s"
                  }],
                  "tenders":%s
                }
                """.formatted(
                fixture.customerId(),
                paymentType,
                orderDiscount,
                charges,
                roundOff,
                paid,
                confirm,
                fixture.productId(),
                quantity,
                price,
                discount,
                tax,
                tenders == null ? "null" : tenders);
    }

    private String updateJson(Fixture fixture, String quantity, String price, Number version, boolean confirm) {
        return """
                {
                  "customerId":%d,
                  "invoiceDate":"2026-10-02",
                  "paymentType":"CREDIT",
                  "orderDiscount":"0",
                  "additionalCharges":"0",
                  "roundOff":"0",
                  "paidAmount":"0",
                  "confirm":%s,
                  "version":%s,
                  "items":[{
                    "productId":%s,
                    "quantity":"%s",
                    "sellingPrice":"%s",
                    "discount":"0",
                    "tax":"0"
                  }]
                }
                """.formatted(fixture.customerId(), confirm, version, fixture.productId(), quantity, price);
    }

    private String returnJson(Number saleId, Number version, Number itemId, String quantity) {
        return """
                {
                  "saleId":%s,
                  "saleVersion":%s,
                  "returnDate":"2026-10-02",
                  "reason":"Customer changed their mind",
                  "refundMethod":"CREDIT",
                  "items":[{"saleItemId":%s,"quantity":"%s"}]
                }
                """.formatted(saleId, version, itemId, quantity);
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
        return readToken(result);
    }

    private String readToken(MvcResult result) throws Exception {
        return JsonPath.parse(result.getResponse().getContentAsString()).read("$.data.accessToken", String.class);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            return null;
        }
        return readToken(result);
    }

    private record Fixture(long customerId, Number productId, String barcode, long supplierId) {
    }
}
