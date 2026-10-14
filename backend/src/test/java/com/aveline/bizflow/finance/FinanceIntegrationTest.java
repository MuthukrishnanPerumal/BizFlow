package com.aveline.bizflow.finance;

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
class FinanceIntegrationTest {

    private static final String INITIAL_PASSWORD = "Aveline#Admin1";
    private static final String REPLACEMENT_PASSWORD = "Aveline#Admin2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.aveline.bizflow.identity.RoleRepository roleRepository;

    @Test
    void anonymousAndUnauthorizedUsersCannotPostPaymentsOrExpenses() throws Exception {
        mockMvc.perform(get("/api/v1/customer-payments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/receivables")).andExpect(status().isUnauthorized());

        String admin = adminToken();
        long roleId = roleRepository.findByCode("INVENTORY_USER").orElseThrow().getId();
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"stock.finance",
                                  "fullName":"Stock Finance",
                                  "email":"stock.finance@aveline.local",
                                  "password":"Viewer#Pass1",
                                  "status":"ACTIVE",
                                  "roleIds":[%d]
                                }
                                """.formatted(roleId)))
                .andExpect(status().isCreated());
        String stock = userToken("stock.finance", "Viewer#Pass1", "Viewer#Pass2");
        mockMvc.perform(post("/api/v1/customer-payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stock)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":1,"paymentDate":"2026-10-03","amount":"1.0000","paymentMethod":"CASH"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stock)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expenseDate":"2026-10-03","category":"OFFICE","description":"Paper","amount":"1.0000","paymentMethod":"CASH"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/payables").header(HttpHeaders.AUTHORIZATION, "Bearer " + stock))
                .andExpect(status().isForbidden());
    }

    @Test
    void recordsPartialFullAndCancelledCustomerPayments() throws Exception {
        String admin = adminToken();
        Books books = books(admin, "30");
        Number saleId = creditSale(admin, books, "80.0000");

        MvcResult partial = mockMvc.perform(post("/api/v1/customer-payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payment(books.customerId(), "30.0000", saleId, "30.0000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentNumber", startsWith("PAY-2026-")))
                .andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.allocatedAmount").value(30.0))
                .andExpect(jsonPath("$.data.unallocatedAmount").value(0.0))
                .andExpect(jsonPath("$.data.partyOutstanding").value(50.0))
                .andExpect(jsonPath("$.data.journal[0].debit").value(30.0))
                .andExpect(jsonPath("$.data.journal[1].credit").value(30.0))
                .andExpect(jsonPath("$.data.audits[0].eventType").value("PAYMENT_CREATED"))
                .andReturn();
        mockMvc.perform(get("/api/v1/sales/" + saleId).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.balanceAmount").value(50.0))
                .andExpect(jsonPath("$.data.paidAmount").value(30.0));

        MvcResult full = mockMvc.perform(post("/api/v1/customer-payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payment(books.customerId(), "50.0000", saleId, "50.0000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.partyOutstanding").value(0.0))
                .andReturn();
        mockMvc.perform(get("/api/v1/sales/" + saleId).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.balanceAmount").value(0.0));

        Number fullId = JsonPath.read(full.getResponse().getContentAsString(), "$.data.id");
        Number fullVersion = JsonPath.read(full.getResponse().getContentAsString(), "$.data.version");
        MvcResult cancelled = mockMvc.perform(post("/api/v1/customer-payments/" + fullId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + fullVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.partyOutstanding").value(50.0))
                .andExpect(jsonPath("$.data.audits[0].eventType").value("PAYMENT_CANCELLED"))
                .andReturn();
        Number cancelledVersion = JsonPath.read(cancelled.getResponse().getContentAsString(), "$.data.version");
        mockMvc.perform(get("/api/v1/sales/" + saleId).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.balanceAmount").value(50.0));
        mockMvc.perform(put("/api/v1/customer-payments/" + fullId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId":%d,
                                  "paymentDate":"2026-10-03",
                                  "amount":"10.0000",
                                  "paymentMethod":"CASH",
                                  "allocations":[{"invoiceId":%s,"amount":"10.0000"}],
                                  "version":%s
                                }
                                """.formatted(books.customerId(), saleId, cancelledVersion)))
                .andExpect(status().isUnprocessableEntity());

        Number partialId = JsonPath.read(partial.getResponse().getContentAsString(), "$.data.id");
        Number partialVersion = JsonPath.read(partial.getResponse().getContentAsString(), "$.data.version");
        mockMvc.perform(put("/api/v1/customer-payments/" + partialId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId":%d,
                                  "paymentDate":"2026-10-03",
                                  "amount":"40.0000",
                                  "paymentMethod":"UPI",
                                  "referenceNumber":"UPI-9",
                                  "notes":"Corrected receipt",
                                  "allocations":[{"invoiceId":%s,"amount":"40.0000"}],
                                  "version":%s
                                }
                                """.formatted(books.customerId(), saleId, partialVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(40.0))
                .andExpect(jsonPath("$.data.partyOutstanding").value(40.0))
                .andExpect(jsonPath("$.data.audits[0].eventType").value("PAYMENT_UPDATED"));
    }

    @Test
    void allocatesOneReceiptAcrossInvoicesAndKeepsAnAdvance() throws Exception {
        String admin = adminToken();
        Books books = books(admin, "30");
        Number first = creditSale(admin, books, "80.0000");
        Number second = creditSale(admin, books, "40.0000");
        mockMvc.perform(post("/api/v1/customer-payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId":%d,
                                  "paymentDate":"2026-10-03",
                                  "amount":"100.0000",
                                  "paymentMethod":"BANK_TRANSFER",
                                  "referenceNumber":"NEFT-1",
                                  "allocations":[
                                    {"invoiceId":%s,"amount":"80.0000"},
                                    {"invoiceId":%s,"amount":"20.0000"}
                                  ]
                                }
                                """.formatted(books.customerId(), first, second)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.allocatedAmount").value(100.0))
                .andExpect(jsonPath("$.data.partyOutstanding").value(20.0));
        mockMvc.perform(get("/api/v1/sales/" + first).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        mockMvc.perform(get("/api/v1/sales/" + second).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$.data.balanceAmount").value(20.0));

        mockMvc.perform(post("/api/v1/customer-payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId":%d,
                                  "paymentDate":"2026-10-03",
                                  "amount":"5.0000",
                                  "paymentMethod":"CASH",
                                  "allocations":[]
                                }
                                """.formatted(books.customerId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.unallocatedAmount").value(5.0))
                .andExpect(jsonPath("$.data.journal[1].accountCode").value("2150"))
                .andExpect(jsonPath("$.data.partyOutstanding").value(15.0));
        mockMvc.perform(get("/api/v1/sales/" + second).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$.data.balanceAmount").value(20.0));
    }

    @Test
    void recordsSupplierPaymentsAndExpenseLifecycle() throws Exception {
        String admin = adminToken();
        Books books = books(admin, "30");
        mockMvc.perform(get("/api/v1/suppliers/" + books.supplierId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$.data.account.balance").value(50.0));
        Number purchaseId = JsonPath.read(mockMvc.perform(get("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("supplierId", String.valueOf(books.supplierId())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.items[0].id");

        mockMvc.perform(post("/api/v1/supplier-payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId":%d,
                                  "paymentDate":"2026-10-03",
                                  "amount":"20.0000",
                                  "paymentMethod":"CASH",
                                  "allocations":[{"invoiceId":%s,"amount":"20.0000"}]
                                }
                                """.formatted(books.supplierId(), purchaseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.partyOutstanding").value(30.0));
        mockMvc.perform(post("/api/v1/supplier-payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId":%d,
                                  "paymentDate":"2026-10-03",
                                  "amount":"30.0000",
                                  "paymentMethod":"CARD",
                                  "allocations":[{"invoiceId":%s,"amount":"30.0000"}]
                                }
                                """.formatted(books.supplierId(), purchaseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.partyOutstanding").value(0.0));
        mockMvc.perform(get("/api/v1/purchases/" + purchaseId).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.balanceAmount").value(0.0));

        MvcResult expense = mockMvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expenseDate":"2026-10-03",
                                  "category":"OFFICE",
                                  "description":"Printer paper",
                                  "amount":"25.0000",
                                  "paymentMethod":"CASH",
                                  "referenceNumber":"BILL-9"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.expenseNumber", startsWith("EXP-2026-")))
                .andExpect(jsonPath("$.data.status").value("POSTED"))
                .andExpect(jsonPath("$.data.journal[0].accountCode").value("5500"))
                .andExpect(jsonPath("$.data.journal[1].accountCode").value("1100"))
                .andExpect(jsonPath("$.data.audits[0].eventType").value("EXPENSE_CREATED"))
                .andReturn();
        Number expenseId = JsonPath.read(expense.getResponse().getContentAsString(), "$.data.id");
        Number expenseVersion = JsonPath.read(expense.getResponse().getContentAsString(), "$.data.version");
        mockMvc.perform(put("/api/v1/expenses/" + expenseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expenseDate":"2026-10-03",
                                  "category":"RENT",
                                  "description":"Printer paper",
                                  "amount":"40.0000",
                                  "paymentMethod":"UPI",
                                  "version":%s
                                }
                                """.formatted(expenseVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(40.0))
                .andExpect(jsonPath("$.data.journal[0].accountCode").value("5100"));
        Number updatedVersion = JsonPath.read(mockMvc.perform(get("/api/v1/expenses/" + expenseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andReturn().getResponse().getContentAsString(), "$.data.version");
        mockMvc.perform(post("/api/v1/expenses/" + expenseId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + updatedVersion + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.journalStatus").value("REVERSED"))
                .andExpect(jsonPath("$.data.audits[0].eventType").value("EXPENSE_CANCELLED"));
        mockMvc.perform(post("/api/v1/expenses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expenseDate":"2026-10-03","category":"OFFICE","description":"Zero","amount":"0.0000","paymentMethod":"CASH"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("AMOUNT"));
    }

    @Test
    void receivablesAndPayablesExposeOutstandingAndOverdue() throws Exception {
        String admin = adminToken();
        Books books = books(admin, "0");
        creditSale(admin, books, "80.0000");
        mockMvc.perform(get("/api/v1/receivables")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("customerId", String.valueOf(books.customerId()))
                        .param("status", "OVERDUE")
                        .param("minAmount", "80.0000")
                        .param("maxAmount", "80.0000")
                        .param("dateFrom", "2026-10-01")
                        .param("dateTo", "2026-10-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].customerName").value(org.hamcrest.Matchers.containsString("Finance customer")))
                .andExpect(jsonPath("$.data.items[0].totalSales").value(80.0))
                .andExpect(jsonPath("$.data.items[0].outstanding").value(80.0))
                .andExpect(jsonPath("$.data.items[0].overdueAmount").value(80.0))
                .andExpect(jsonPath("$.data.items[0].status").value("OVERDUE"))
                .andExpect(jsonPath("$.data.totalOutstanding").value(80.0));
        mockMvc.perform(get("/api/v1/payables")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("supplierId", String.valueOf(books.supplierId()))
                        .param("status", "OVERDUE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].totalPurchases").value(50.0))
                .andExpect(jsonPath("$.data.items[0].outstanding").value(50.0))
                .andExpect(jsonPath("$.data.items[0].status").value("OVERDUE"));
    }

    @Test
    void rejectsInvalidPartiesAndAmounts() throws Exception {
        String admin = adminToken();
        mockMvc.perform(post("/api/v1/customer-payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":999999,"paymentDate":"2026-10-03","amount":"10.0000","paymentMethod":"CASH"}
                                """))
                .andExpect(status().isNotFound());
        Number active = idOf(mockMvc.perform(post("/api/v1/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerCode":"FIN-ON",
                                  "customerName":"Active finance",
                                  "phone":"9876500098",
                                  "openingBalance":"0",
                                  "creditLimit":"0",
                                  "status":"ACTIVE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(post("/api/v1/customer-payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%s,"paymentDate":"2026-10-03","amount":"-5.0000","paymentMethod":"CASH"}
                                """.formatted(active)))
                .andExpect(status().isUnprocessableEntity());
        Number inactive = idOf(mockMvc.perform(post("/api/v1/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerCode":"FIN-OFF",
                                  "customerName":"Inactive finance",
                                  "phone":"9876500099",
                                  "openingBalance":"0",
                                  "creditLimit":"0",
                                  "status":"INACTIVE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(post("/api/v1/customer-payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":%s,"paymentDate":"2026-10-03","amount":"10.0000","paymentMethod":"CASH"}
                                """.formatted(inactive)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("INACTIVE"));
        mockMvc.perform(post("/api/v1/supplier-payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierId":999999,"paymentDate":"2026-10-03","amount":"10.0000","paymentMethod":"CASH"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void overAllocationRollsBackTheReceiptAndInvoiceBalance() throws Exception {
        String admin = adminToken();
        try {
            Books books = books(admin, "30");
            Number saleId = creditSale(admin, books, "80.0000");
            mockMvc.perform(post("/api/v1/customer-payments")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payment(books.customerId(), "100.0000", saleId, "100.0000")))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errors[0].code").value("ALLOCATION"));
            mockMvc.perform(get("/api/v1/sales/" + saleId).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                    .andExpect(jsonPath("$.data.balanceAmount").value(80.0))
                    .andExpect(jsonPath("$.data.paidAmount").value(0.0));
            mockMvc.perform(get("/api/v1/customers/" + books.customerId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                    .andExpect(jsonPath("$.data.account.balance").value(80.0));
            mockMvc.perform(get("/api/v1/customer-payments")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .param("customerId", String.valueOf(books.customerId())))
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        } finally {
            restoreAdminPassword();
        }
    }

    private Number creditSale(String token, Books books, String price) throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/sales")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId":%d,
                                  "invoiceDate":"2026-10-02",
                                  "paymentType":"CREDIT",
                                  "paidAmount":"0",
                                  "confirm":true,
                                  "items":[{
                                    "productId":%d,
                                    "quantity":"1",
                                    "sellingPrice":"%s",
                                    "discount":"0",
                                    "tax":"0"
                                  }]
                                }
                                """.formatted(books.customerId(), books.productId(), price)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private String payment(long customerId, String amount, Number saleId, String allocated) {
        return """
                {
                  "customerId":%d,
                  "paymentDate":"2026-10-03",
                  "amount":"%s",
                  "paymentMethod":"CASH",
                  "referenceNumber":"RCPT-1",
                  "notes":"Counter receipt",
                  "allocations":[{"invoiceId":%s,"amount":"%s"}]
                }
                """.formatted(customerId, amount, saleId, allocated);
    }

    private Books books(String token, String terms) throws Exception {
        String mark = Long.toString(System.nanoTime(), 36).toUpperCase();
        String suffix = mark.substring(Math.max(0, mark.length() - 8));
        Number categoryId = idOf(mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"FC" + suffix + "\",\"name\":\"Finance " + suffix + "\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        Number brandId = idOf(mockMvc.perform(post("/api/v1/brands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"FB" + suffix + "\",\"name\":\"Brand " + suffix + "\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andReturn());
        Number unitId = JsonPath.read(mockMvc.perform(get("/api/v1/units")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("search", "PCS"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.items[0].id");
        Number productId = idOf(mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode":"FIN-%s",
                                  "barcode":"79%s",
                                  "name":"Finance item %s",
                                  "categoryId":%s,
                                  "brandId":%s,
                                  "unitId":%s,
                                  "taxPercent":"0.0000",
                                  "purchasePrice":"10.0000",
                                  "sellingPrice":"80.0000",
                                  "mrp":"100.0000",
                                  "minimumSellingPrice":"0.0000",
                                  "reorderLevel":"0",
                                  "stockType":"PICS",
                                  "batchTracking":false,
                                  "serialTracking":false,
                                  "expiryTracking":false,
                                  "status":"ACTIVE"
                                }
                                """.formatted(suffix, suffix, suffix, categoryId, brandId, unitId)))
                .andExpect(status().isCreated())
                .andReturn());
        Number supplierId = idOf(mockMvc.perform(post("/api/v1/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierCode":"FS%s",
                                  "supplierName":"Finance supplier %s",
                                  "phone":"9876543210",
                                  "openingBalance":"0",
                                  "creditLimit":"50000",
                                  "paymentTerms":"%s",
                                  "status":"ACTIVE"
                                }
                                """.formatted(suffix, suffix, terms)))
                .andExpect(status().isCreated())
                .andReturn());
        Number customerId = idOf(mockMvc.perform(post("/api/v1/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerCode":"FC%s",
                                  "customerName":"Finance customer %s",
                                  "phone":"9876500000",
                                  "openingBalance":"0",
                                  "creditLimit":"50000",
                                  "paymentTerms":"%s",
                                  "status":"ACTIVE"
                                }
                                """.formatted(suffix, suffix, terms)))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(post("/api/v1/purchases")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId":%s,
                                  "invoiceNumber":"FIN-%s",
                                  "purchaseDate":"2026-10-02",
                                  "paymentType":"CREDIT",
                                  "paidAmount":"0",
                                  "confirm":true,
                                  "items":[{
                                    "productId":%s,
                                    "quantity":"5",
                                    "freeQuantity":"0",
                                    "purchasePrice":"10.0000",
                                    "discount":"0",
                                    "tax":"0"
                                  }]
                                }
                                """.formatted(supplierId, suffix, productId)))
                .andExpect(status().isCreated());
        return new Books(customerId.longValue(), supplierId.longValue(), productId.longValue());
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

    private record Books(long customerId, long supplierId, long productId) {
    }
}
