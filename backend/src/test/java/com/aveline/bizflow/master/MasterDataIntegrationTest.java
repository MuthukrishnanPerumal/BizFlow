package com.aveline.bizflow.master;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MasterDataIntegrationTest {

    private static final String INITIAL_PASSWORD = "Aveline#Admin1";
    private static final String REPLACEMENT_PASSWORD = "Aveline#Admin2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.aveline.bizflow.identity.RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void anonymousAndViewOnlyAccessAreEnforced() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());

        String admin = adminToken();
        long roleId = roleRepository.findByCode("INVENTORY_USER").orElseThrow().getId();
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"stock.user",
                                  "fullName":"Stock User",
                                  "email":"stock.user@aveline.local",
                                  "password":"Viewer#Pass1",
                                  "status":"ACTIVE",
                                  "roleIds":[%d]
                                }
                                """.formatted(roleId)))
                .andExpect(status().isCreated());
        String stock = userToken("stock.user", "Viewer#Pass1", "Viewer#Pass2");

        mockMvc.perform(get("/api/v1/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + stock))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stock)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("DENIED", "Denied category")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/suppliers").header(HttpHeaders.AUTHORIZATION, "Bearer " + stock))
                .andExpect(status().isForbidden());
    }

    @Test
    void categoryDuplicatePreventionSoftDeleteAndReuse() throws Exception {
        String admin = adminToken();
        MvcResult created = createCategory(admin, "GROCERY", "Grocery");
        Number id = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");
        Number version = JsonPath.read(created.getResponse().getContentAsString(), "$.data.version");

        mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("GROCERY", "Another grocery")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("code"));

        mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("GROCERY2", "Grocery")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("name"));

        mockMvc.perform(put("/api/v1/categories/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"GROCERY","name":"Grocery aisle","description":"Food","status":"ACTIVE","version":999}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/categories/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"GROCERY","name":"Grocery aisle","description":"Food","status":"INACTIVE","version":%s}
                                """.formatted(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(get("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("search", "Grocery aisle")
                        .param("status", "INACTIVE")
                        .param("sort", "name,asc")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].code").value("GROCERY"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(delete("/api/v1/categories/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(get("/api/v1/categories/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNotFound());

        createCategory(admin, "GROCERY", "Grocery");
    }

    @Test
    void productRulesSearchBarcodeAndReferenceProtection() throws Exception {
        String admin = adminToken();
        Number categoryId = idOf(createCategory(admin, "WIDGETS", "Widgets"));
        Number brandId = idOf(mockMvc.perform(post("/api/v1/brands")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ACME","name":"Acme","description":"House brand","status":"ACTIVE"}
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        Number pieceId = unitId(admin, "PCS");

        mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("WDG-1", "8901001", categoryId, brandId, pieceId, "120.0000", "100.0000", "1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("sellingPrice"));

        mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("WDG-1", "8901001", 999999L, brandId, pieceId, "80.0000", "100.0000", "1")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));

        mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("WDG-1", "8901001", categoryId, brandId, pieceId, "80.0000", "100.0000", "1.5000")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("reorderLevel"));

        MvcResult product = mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("WDG-1", "8901001", categoryId, brandId, pieceId, "80.0000", "100.0000", "2")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.category.code").value("WIDGETS"))
                .andExpect(jsonPath("$.data.stockType").value("NONE"))
                .andReturn();
        Number productId = JsonPath.read(product.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("WDG-2", "8901001", categoryId, brandId, pieceId, "80.0000", "100.0000", "2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("barcode"));

        mockMvc.perform(get("/api/v1/products/lookup")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("barcode", "8901001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCode").value("WDG-1"));

        mockMvc.perform(get("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("search", "8901001")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(productId))
                .andExpect(jsonPath("$.data.size").value(1));

        mockMvc.perform(delete("/api/v1/categories/" + categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("This category is used by products. Set it to inactive instead of deleting it."));

        mockMvc.perform(delete("/api/v1/products/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(get("/api/v1/products/" + productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/categories/" + categoryId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk());
    }

    @Test
    void seededUnitsAndPartyLedgers() throws Exception {
        String admin = adminToken();
        mockMvc.perform(get("/api/v1/units")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("search", "Kilogram")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].shortName").value("KG"))
                .andExpect(jsonPath("$.data.items[0].decimalAllowed").value(true));

        mockMvc.perform(post("/api/v1/units")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Piece","shortName":"PCS2","description":"Duplicate","decimalAllowed":false,"status":"ACTIVE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("name"));

        MvcResult supplier = mockMvc.perform(post("/api/v1/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierJson("SUP-1", "Northwind Traders", "250.0000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.account.balance").value(250.0))
                .andReturn();
        Number supplierId = JsonPath.read(supplier.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/suppliers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supplierJson("SUP-1", "Other Traders", "0")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("supplierCode"));

        mockMvc.perform(get("/api/v1/suppliers/" + supplierId + "/ledger")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].entryType").value("OPENING"))
                .andExpect(jsonPath("$.data.items[0].creditAmount").value(250.0))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        MvcResult customer = mockMvc.perform(post("/api/v1/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerJson("CUS-1", "Ada Stores", "80.0000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.account.balance").value(80.0))
                .andReturn();
        Number customerId = JsonPath.read(customer.getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(get("/api/v1/customers/" + customerId + "/ledger")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].debitAmount").value(80.0));

        mockMvc.perform(get("/api/v1/customers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("search", "Ada")
                        .param("sort", "customerName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].customerCode").value("CUS-1"));
    }

    private MvcResult createCategory(String token, String code, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson(code, name)))
                .andExpect(status().isCreated())
                .andReturn();
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

    private String categoryJson(String code, String name) {
        return """
                {"code":"%s","name":"%s","description":"Catalog group","status":"ACTIVE"}
                """.formatted(code, name);
    }

    private String productJson(
            String code,
            String barcode,
            Number categoryId,
            Number brandId,
            Number unitId,
            String sellingPrice,
            String mrp,
            String reorderLevel) {
        return """
                {
                  "productCode":"%s",
                  "barcode":"%s",
                  "name":"Widget",
                  "shortName":"Widget",
                  "description":"Test widget",
                  "categoryId":%s,
                  "brandId":%s,
                  "unitId":%s,
                  "taxPercent":"18.0000",
                  "purchasePrice":"40.0000",
                  "sellingPrice":"%s",
                  "mrp":"%s",
                  "minimumSellingPrice":"70.0000",
                  "reorderLevel":"%s",
                  "maximumStockLevel":"20",
                  "stockType":"NONE",
                  "batchTracking":false,
                  "serialTracking":false,
                  "expiryTracking":false,
                  "status":"ACTIVE"
                }
                """.formatted(code, barcode, categoryId, brandId, unitId, sellingPrice, mrp, reorderLevel);
    }

    private String supplierJson(String code, String name, String opening) {
        return """
                {
                  "supplierCode":"%s",
                  "supplierName":"%s",
                  "companyName":"%s",
                  "contactPerson":"Asha",
                  "phone":"9876543210",
                  "email":"buyer@northwind.test",
                  "taxNumber":"29ABCDE1234F1Z5",
                  "address":"12 Market Road",
                  "country":"India",
                  "state":"Karnataka",
                  "city":"Bengaluru",
                  "pincode":"560001",
                  "openingBalance":"%s",
                  "creditLimit":"5000.0000",
                  "paymentTerms":"Net 30",
                  "status":"ACTIVE"
                }
                """.formatted(code, name, name, opening);
    }

    private String customerJson(String code, String name, String opening) {
        return """
                {
                  "customerCode":"%s",
                  "customerName":"%s",
                  "companyName":"%s",
                  "phone":"9876500000",
                  "email":"ada@stores.test",
                  "address":"8 Lake Street",
                  "country":"India",
                  "state":"Karnataka",
                  "city":"Mysuru",
                  "pincode":"570001",
                  "openingBalance":"%s",
                  "creditLimit":"2000",
                  "paymentTerms":"Due on receipt",
                  "status":"ACTIVE"
                }
                """.formatted(code, name, name, opening);
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
}
