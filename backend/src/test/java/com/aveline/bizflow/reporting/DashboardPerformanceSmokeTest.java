package com.aveline.bizflow.reporting;

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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Local latency smoke for dashboard after query consolidation.
 * Measures in-process MockMvc time (excludes network/TLS/cold-start).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardPerformanceSmokeTest {

    private static final String INITIAL_PASSWORD = "Aveline#Admin1";
    private static final String REPLACEMENT_PASSWORD = "Aveline#Admin2";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void emptyDashboardIsWarmAndIncludesDraftCounts() throws Exception {
        String admin = adminToken();
        // Warm Hibernate / JDBC once.
        mockMvc.perform(get("/api/v1/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .param("preset", "CUSTOM")
                        .param("from", "1980-01-01")
                        .param("to", "1980-01-07"))
                .andExpect(status().isOk());

        List<Long> samples = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            long started = System.nanoTime();
            mockMvc.perform(get("/api/v1/dashboard")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                            .param("preset", "CUSTOM")
                            .param("from", "1980-01-01")
                            .param("to", "1980-01-07"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.drafts.sales").value(0))
                    .andExpect(jsonPath("$.data.drafts.purchases").value(0));
            samples.add((System.nanoTime() - started) / 1_000_000L);
        }
        long median = samples.stream().sorted().skip(samples.size() / 2).findFirst().orElse(0L);
        // Soft guard: empty local dashboard should stay well under 1s in-process.
        assertTrue(median < 1000, "median empty dashboard ms=" + median + " samples=" + samples);
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
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }
}
