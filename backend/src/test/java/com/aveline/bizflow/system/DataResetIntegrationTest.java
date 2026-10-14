package com.aveline.bizflow.system;

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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DataResetIntegrationTest {

    private static final String INITIAL_PASSWORD = "Aveline#Admin1";
    private static final String REPLACEMENT_PASSWORD = "Aveline#Admin2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void hardDeletesOnlySelectedTables() throws Exception {
        mockMvc.perform(get("/api/v1/settings/data-reset")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/settings/data-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tables\":[\"suppliers\"]}"))
                .andExpect(status().isUnauthorized());

        String admin = adminToken();
        mockMvc.perform(get("/api/v1/settings/data-reset").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.key == 'suppliers')].table", hasItem("suppliers")))
                .andExpect(jsonPath("$.data[?(@.key == 'users')]").isEmpty())
                .andExpect(jsonPath("$.data[?(@.table == 'platform_setting')]").isEmpty())
                .andExpect(jsonPath("$.data.length()", greaterThan(10)));

        String code = "RST" + System.nanoTime();
        jdbcTemplate.update("""
                INSERT INTO suppliers (
                    supplier_code, supplier_name, opening_balance, credit_limit, status,
                    created_at, updated_at, created_by, updated_by, is_deleted, version
                ) VALUES (?, 'Reset Supplier', 0, 0, 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'test', 'test', 0, 0)
                """, code);
        Long supplierId = jdbcTemplate.queryForObject("SELECT id FROM suppliers WHERE supplier_code = ?", Long.class, code);
        jdbcTemplate.update("""
                INSERT INTO supplier_ledger_entry (
                    supplier_id, entry_type, entry_date, debit_amount, credit_amount,
                    created_at, updated_at, created_by, updated_by, is_deleted, version
                ) VALUES (?, 'ADJUSTMENT', CURRENT_DATE, 1, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'test', 'test', 0, 0)
                """, supplierId);

        mockMvc.perform(post("/api/v1/settings/data-reset")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tables\":[\"suppliers\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("Supplier ledger")));
        Integer supplierStillThere = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM suppliers WHERE supplier_code = ?", Integer.class, code);
        org.junit.jupiter.api.Assertions.assertEquals(1, supplierStillThere);

        jdbcTemplate.update("""
                INSERT INTO app_notification (kind, severity, title, message, created_at)
                VALUES ('LOW_STOCK', 'WARNING', 'Reset probe', 'Reset probe', UTC_TIMESTAMP(6))
                """);
        mockMvc.perform(post("/api/v1/settings/data-reset")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tables\":[\"app_notification\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tables[0].key").value("app_notification"))
                .andExpect(jsonPath("$.data.deletedRows", greaterThan(0)));
        Integer notificationsLeft = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_notification", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, notificationsLeft);
        Integer supplierLeft = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM suppliers WHERE id = ?", Integer.class, supplierId);
        org.junit.jupiter.api.Assertions.assertEquals(1, supplierLeft);
        Integer settingsLeft = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM platform_setting", Integer.class);
        org.junit.jupiter.api.Assertions.assertTrue(settingsLeft != null && settingsLeft > 0);

        mockMvc.perform(post("/api/v1/settings/data-reset")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tables\":[\"users\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", not(containsString("deleted"))));
        Integer admins = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = 'admin'", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, admins);
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
