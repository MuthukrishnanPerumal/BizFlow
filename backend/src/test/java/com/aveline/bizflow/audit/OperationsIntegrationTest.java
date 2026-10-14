package com.aveline.bizflow.audit;

import com.aveline.bizflow.notification.AppNotificationService;
import com.aveline.bizflow.notification.NotificationKind;
import com.aveline.bizflow.notification.NotificationSeverity;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OperationsIntegrationTest {

    private static final String INITIAL_PASSWORD = "Aveline#Admin1";
    private static final String REPLACEMENT_PASSWORD = "Aveline#Admin2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditRecorder auditRecorder;

    @Autowired
    private AppNotificationService notifications;

    @Autowired
    private com.aveline.bizflow.identity.RoleRepository roleRepository;

    @Test
    void auditFiltersExportsSettingsAndNotifications() throws Exception {
        mockMvc.perform(get("/api/v1/audit")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/settings").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        String admin = adminToken();
        auditRecorder.recordAs("auditor.one", "AUTH_LOGIN", "login", AuditOutcome.SUCCESS, "user", "11", "alpha-marker");
        auditRecorder.recordAs("auditor.one", "AUTH_LOGOUT", "logout", AuditOutcome.SUCCESS, "user", "11", "alpha-second");
        auditRecorder.recordAs("auditor.two", "SETTINGS_UPDATED", "settings", AuditOutcome.SUCCESS, "settings", "business", "beta-marker");

        mockMvc.perform(get("/api/v1/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("user", "auditor.one")
                        .param("action", "login")
                        .param("module", "AUTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].user").value("auditor.one"))
                .andExpect(jsonPath("$.data.items[0].module").value("AUTH"))
                .andExpect(jsonPath("$.data.items[0].entity").value("user"));

        mockMvc.perform(get("/api/v1/audit/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("format", "csv")
                        .param("user", "auditor.one")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("auditor.one")))
                .andExpect(content().string(containsString("alpha-marker")))
                .andExpect(content().string(containsString("alpha-second")))
                .andExpect(content().string(not(containsString("auditor.two"))))
                .andExpect(content().string(not(containsString("beta-marker"))));

        mockMvc.perform(get("/api/v1/audit/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("format", "pdf")
                        .param("user", "auditor.two"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("%PDF-1.4")))
                .andExpect(content().string(containsString("beta-marker")))
                .andExpect(content().string(not(containsString("alpha-marker"))));

        MvcResult current = mockMvc.perform(get("/api/v1/settings").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.invoicePrefix").value("SAL"))
                .andExpect(jsonPath("$.data.allowNegativeStock").value(false))
                .andReturn();
        int version = ((Number) JsonPath.read(current.getResponse().getContentAsString(), "$.data.version")).intValue();
        String body = settingsBody(version, "Aveline North", "password=should-not-stick");
        mockMvc.perform(put("/api/v1/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("Aveline North"))
                .andExpect(jsonPath("$.data.companyAddress").value("password=should-not-stick"));

        MvcResult settingsAudit = mockMvc.perform(get("/api/v1/audit")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("action", "settings")
                        .param("module", "SETTINGS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").exists())
                .andReturn();
        int auditId = ((Number) JsonPath.read(settingsAudit.getResponse().getContentAsString(), "$.data.items[0].id")).intValue();
        mockMvc.perform(get("/api/v1/audit/" + auditId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.oldValue", containsString("Aveline BizFlow")))
                .andExpect(jsonPath("$.data.newValue", containsString("Aveline North")))
                .andExpect(jsonPath("$.data.newValue", containsString("[redacted]")))
                .andExpect(jsonPath("$.data.newValue", not(containsString("should-not-stick"))));

        long roleId = roleRepository.findByCode("INVENTORY_USER").orElseThrow().getId();
        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"stock.ops",
                                  "fullName":"Stock Ops",
                                  "email":"stock.ops@aveline.local",
                                  "password":"Viewer#Pass1",
                                  "status":"ACTIVE",
                                  "roleIds":[%d]
                                }
                                """.formatted(roleId)))
                .andExpect(status().isCreated());
        String stock = userToken("stock.ops", "Viewer#Pass1", "Viewer#Pass2");
        mockMvc.perform(put("/api/v1/settings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stock)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/settings").header(HttpHeaders.AUTHORIZATION, "Bearer " + stock))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/audit").header(HttpHeaders.AUTHORIZATION, "Bearer " + stock))
                .andExpect(status().isForbidden());

        notifications.raise(
                NotificationKind.LOW_STOCK,
                NotificationSeverity.WARNING,
                "Low stock",
                "Widget is at or below its reorder level.",
                "INVENTORY",
                "product",
                "ops-1");
        notifications.raise(
                NotificationKind.LOW_STOCK,
                NotificationSeverity.WARNING,
                "Low stock",
                "Widget is at or below its reorder level.",
                "INVENTORY",
                "product",
                "ops-1");
        MvcResult listed = mockMvc.perform(get("/api/v1/notifications")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("kind", "LOW_STOCK")
                        .param("search", "ignored"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].kind").value("LOW_STOCK"))
                .andExpect(jsonPath("$.data.items[0].message", containsString("reorder level")))
                .andReturn();
        int id = JsonPath.read(listed.getResponse().getContentAsString(), "$.data.items[0].id");
        mockMvc.perform(post("/api/v1/notifications/" + id + "/read")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());
        mockMvc.perform(get("/api/v1/reports/inventory/print")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", "1980-01-01")
                        .param("to", "1980-01-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    private String settingsBody(int version, String companyName, String address) {
        return """
                {
                  "companyName":"%s",
                  "companyAddress":"%s",
                  "phone":"+91 44 4000 1000",
                  "email":"accounts@aveline.local",
                  "taxName":"GSTIN",
                  "taxNumber":"33AAAAA0000A1Z5",
                  "currencyCode":"INR",
                  "invoicePrefix":"SAL",
                  "purchasePrefix":"PUR",
                  "returnPrefix":"SRT",
                  "purchaseReturnPrefix":"PRT",
                  "roundingRule":"NONE",
                  "allowNegativeStock":false,
                  "defaultPaymentMethod":"CASH",
                  "dateFormat":"medium",
                  "decimalPrecision":2,
                  "version":%d
                }
                """.formatted(companyName, address, version);
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
}
