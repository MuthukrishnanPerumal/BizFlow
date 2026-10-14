package com.aveline.bizflow.identity;

import com.aveline.bizflow.security.JwtProperties;
import com.aveline.bizflow.security.JwtService;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

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
class IdentityAccessIntegrationTest {

    private static final String INITIAL_PASSWORD = "Aveline#Admin1";
    private static final String REPLACEMENT_PASSWORD = "Aveline#Admin2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void successfulLoginReturnsTokenWithoutPasswordMaterial() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("admin", INITIAL_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("admin"))
                .andExpect(jsonPath("$.data.user.permissions").isArray())
                .andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                .andExpect(content().string(not(containsString("$2a$"))));
    }

    @Test
    void invalidPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("admin", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid username or password"))
                .andExpect(content().string(not(containsString("$2a$"))));
    }

    @Test
    void nonexistentUserIsRejectedWithTheSameMessage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("missing.user", INITIAL_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Clock past = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        JwtService issuer = new JwtService(new JwtProperties(jwtProperties.secret(), Duration.ofMinutes(5)), past);
        String token = issuer.generateAccessToken("admin", List.of("SUPER_ADMIN"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void userWithoutPermissionIsForbidden() throws Exception {
        String admin = adminToken();
        long roleId = roleRepository.findByCode("REPORT_VIEWER").orElseThrow().getId();
        createUser(admin, "viewer.one", "viewer.one@aveline.local", roleId, "Viewer#Pass1");
        String viewer = userToken("viewer.one", "Viewer#Pass1", "Viewer#Pass2");

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this action"));
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        String admin = adminToken();
        long roleId = roleRepository.findByCode("REPORT_VIEWER").orElseThrow().getId();
        createUser(admin, "dup.user", "dup.user@aveline.local", roleId, "Viewer#Pass1");

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("dup.user", "other@aveline.local", roleId, "Viewer#Pass1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A user with this username already exists"))
                .andExpect(jsonPath("$.errors[0].field").value("username"));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String admin = adminToken();
        long roleId = roleRepository.findByCode("REPORT_VIEWER").orElseThrow().getId();
        createUser(admin, "email.one", "shared@aveline.local", roleId, "Viewer#Pass1");

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson("email.two", "shared@aveline.local", roleId, "Viewer#Pass1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A user with this email already exists"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void roleAssignmentGrantsPermissionOnTheNextRequest() throws Exception {
        String admin = adminToken();
        long viewerRoleId = roleRepository.findByCode("REPORT_VIEWER").orElseThrow().getId();
        long adminRoleId = roleRepository.findByCode("ADMIN").orElseThrow().getId();
        MvcResult created = createUser(admin, "grant.user", "grant.user@aveline.local", viewerRoleId, "Viewer#Pass1");
        Number userId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");
        String viewer = userToken("grant.user", "Viewer#Pass1", "Viewer#Pass2");

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewer))
                .andExpect(status().isForbidden());

        MvcResult current = mockMvc.perform(get("/api/v1/users/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        Number version = JsonPath.read(current.getResponse().getContentAsString(), "$.data.version");

        mockMvc.perform(put("/api/v1/users/" + userId + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIds\":[" + adminRoleId + "],\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0].code").value("ADMIN"));

        mockMvc.perform(get("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
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
                        .content(credentials(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return readToken(result);
    }

    private MvcResult createUser(String token, String username, String email, long roleId, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson(username, email, roleId, password)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String credentials(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private String userJson(String username, String email, long roleId, String password) {
        return """
                {
                  "username":"%s",
                  "fullName":"Test User",
                  "email":"%s",
                  "phone":"9876543210",
                  "password":"%s",
                  "status":"ACTIVE",
                  "roleIds":[%d]
                }
                """.formatted(username, email, password, roleId);
    }
}
