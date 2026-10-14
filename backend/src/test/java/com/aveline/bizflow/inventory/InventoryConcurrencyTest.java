package com.aveline.bizflow.inventory;

import com.aveline.bizflow.common.exception.BusinessRuleException;
import com.aveline.bizflow.inventory.dto.InventoryDtos.AdjustmentRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryConcurrencyTest {

    private static final String INITIAL_PASSWORD = "Aveline#Admin1";
    private static final String REPLACEMENT_PASSWORD = "Aveline#Admin2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StockAdjustmentService adjustmentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void simultaneousDecreasesCannotOversell() throws Exception {
        String admin = adminToken();
        try {
            long productId = product(admin);
            long locationId = locationId(admin, "MAIN");
            asInventoryUser();
            adjustmentService.create(request(productId, locationId, AdjustmentDirection.INCREASE, "10", AdjustmentReason.OPENING_STOCK));
            int threads = 2;
            AtomicInteger rejected = new AtomicInteger();
            runTogether(threads, () -> {
                try {
                    adjustmentService.create(request(productId, locationId, AdjustmentDirection.DECREASE, "6", AdjustmentReason.DAMAGE));
                } catch (BusinessRuleException ex) {
                    if (!"INSUFFICIENT_STOCK".equals(ex.getCode())) {
                        throw ex;
                    }
                    rejected.incrementAndGet();
                }
            });
            BigDecimal onHand = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(quantity_on_hand), 0) FROM inventory_balance WHERE product_id = ? AND is_deleted = 0",
                    BigDecimal.class,
                    productId);
            assertEquals(0, new BigDecimal("4.0000").compareTo(onHand));
            assertEquals(1, rejected.get());
        } finally {
            SecurityContextHolder.clearContext();
            restoreAdminPassword();
        }
    }

    @Test
    void simultaneousIncreasesKeepEveryUnit() throws Exception {
        String admin = adminToken();
        try {
            long productId = product(admin);
            long locationId = locationId(admin, "MAIN");
            asInventoryUser();
            adjustmentService.create(request(productId, locationId, AdjustmentDirection.INCREASE, "1", AdjustmentReason.FOUND));
            int threads = 8;
            runTogether(threads, () -> retry(() -> adjustmentService.create(
                    request(productId, locationId, AdjustmentDirection.INCREASE, "1", AdjustmentReason.FOUND))));
            BigDecimal onHand = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(quantity_on_hand), 0) FROM inventory_balance WHERE product_id = ? AND is_deleted = 0",
                    BigDecimal.class,
                    productId);
            Integer ledgerRows = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM stock_ledger WHERE product_id = ? AND is_deleted = 0",
                    Integer.class,
                    productId);
            assertEquals(0, new BigDecimal("9.0000").compareTo(onHand));
            assertEquals(threads + 1, ledgerRows);
        } finally {
            SecurityContextHolder.clearContext();
            restoreAdminPassword();
        }
    }

    private void retry(Runnable work) {
        for (int attempt = 1; ; attempt++) {
            try {
                work.run();
                return;
            } catch (CannotAcquireLockException ex) {
                if (attempt == 5) {
                    throw ex;
                }
            }
        }
    }

    private void runTogether(int threads, Runnable work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            futures.add(pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                asInventoryUser();
                try {
                    work.run();
                    return null;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
    }

    private AdjustmentRequest request(
            long productId,
            long locationId,
            AdjustmentDirection direction,
            String quantity,
            AdjustmentReason reason) {
        return new AdjustmentRequest(
                productId,
                locationId,
                null,
                null,
                direction,
                new BigDecimal(quantity),
                null,
                reason,
                "CONC",
                LocalDate.of(2026, 10, 2),
                "Concurrent count",
                null);
    }

    private void asInventoryUser() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin",
                "n/a",
                List.of(new SimpleGrantedAuthority("INVENTORY_ADJUST"))));
        SecurityContextHolder.setContext(context);
    }

    private long product(String token) throws Exception {
        String mark = Long.toUnsignedString(System.nanoTime(), 36).toUpperCase();
        if (mark.length() > 10) {
            mark = mark.substring(mark.length() - 10);
        }
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
        return ((Number) idOf(mockMvc.perform(post("/api/v1/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productCode":"CON-%s",
                                  "barcode":"CN%s",
                                  "name":"Concurrent %s",
                                  "shortName":"CONC",
                                  "description":"Race",
                                  "categoryId":%s,
                                  "brandId":%s,
                                  "unitId":%s,
                                  "taxPercent":"0.0000",
                                  "purchasePrice":"10.0000",
                                  "sellingPrice":"20.0000",
                                  "mrp":"25.0000",
                                  "minimumSellingPrice":"0.0000",
                                  "reorderLevel":"0.0000",
                                  "stockType":"PICS",
                                  "batchTracking":false,
                                  "serialTracking":false,
                                  "expiryTracking":false,
                                  "status":"ACTIVE"
                                }
                                """.formatted(mark, mark, mark, categoryId, brandId, unitId)))
                .andExpect(status().isCreated())
                .andReturn())).longValue();
    }

    private long locationId(String token, String code) throws Exception {
        String body = mockMvc.perform(get("/api/v1/inventory/locations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$.data[?(@.code == '" + code + "')].id");
        return ids.get(0).longValue();
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
