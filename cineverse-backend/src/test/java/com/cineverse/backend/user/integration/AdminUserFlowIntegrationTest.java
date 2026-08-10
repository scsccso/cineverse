package com.cineverse.backend.user.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cineverse.backend.auth.dto.LoginRequest;
import com.cineverse.backend.auth.dto.RegisterRequest;
import com.cineverse.backend.user.dto.UpdateUserRoleRequest;
import com.cineverse.backend.user.entity.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * Admin user management (GET/PATCH/DELETE /api/v1/admin/users) against real
 * Postgres (Testcontainers), through the real Spring Security filter chain —
 * not just AdminUserServiceTest's mocked-repository coverage. The thing a
 * pure service-level test can't prove is that AdminUserController correctly
 * turns {@code Authentication} into "this is the caller's own id": a bug in
 * that wiring (e.g. passing the path variable instead of the authenticated
 * principal) would pass every AdminUserServiceTest case while still leaving
 * the real self-lockout hole open over HTTP. Uses the one seeded ADMIN
 * account (V2__seed_admin.sql) as its own "self" target, mirroring the exact
 * real-world shape of the bug found during review: this project only ever
 * seeds a single ADMIN, so "ADMIN demotes/deletes itself" and "ADMIN
 * demotes/deletes the only other ADMIN" are the same scenario here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminUserFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.security.cookie-secure", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCannotChangeItsOwnRoleOverHttp() throws Exception {
        String adminToken = loginAsAdmin();
        UUID adminId = currentUserId(adminToken);

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", adminId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRoleRequest(Role.CUSTOMER))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot change your own role."));

        // Not just a 409 — the role must genuinely be untouched afterward,
        // not merely rejected-but-partially-applied.
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void adminCannotDeleteItsOwnAccountOverHttp() throws Exception {
        String adminToken = loginAsAdmin();
        UUID adminId = currentUserId(adminToken);

        mockMvc.perform(delete("/api/v1/admin/users/{id}", adminId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot delete your own account."));

        // The account must still be usable, not deleted-then-error.
        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@cineverse.local"));
    }

    @Test
    void adminCanChangeAndDeleteSomeoneElsesAccount() throws Exception {
        String adminToken = loginAsAdmin();
        UUID customerId = registerCustomer();

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", customerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRoleRequest(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(delete("/api/v1/admin/users/{id}", customerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    private String loginAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin@cineverse.local", "Admin@12345"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private UUID registerCustomer() throws Exception {
        String email = "admin-user-flow-" + UUID.randomUUID() + "@cineverse.local";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, "Sup3rSecret!", "Admin Flow Customer"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID currentUserId(String accessToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }
}
