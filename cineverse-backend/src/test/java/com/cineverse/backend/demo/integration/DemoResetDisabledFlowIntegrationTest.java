package com.cineverse.backend.demo.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A separate Spring context from DemoResetFlowIntegrationTest — the "not
 * configured at all" scenario needs app.demo-reset.secret to actually be
 * blank, which can't coexist in the same context as the other class's
 * "always configured" tests. No Redis container needed: verifySecret
 * rejects before either endpoint ever reaches DemoResetService's Redis
 * cleanup step, same reasoning ShowtimeAdminFlowIntegrationTest already
 * relies on for not needing one either.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DemoResetDisabledFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.security.cookie-secure", () -> "false");
        // Explicit, not just relying on DEMO_RESET_SECRET being absent from
        // this process's real environment — guarantees the "unconfigured"
        // condition regardless of what happens to be set on the host
        // actually running this test.
        registry.add("app.demo-reset.secret", () -> "");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void resetTransactionsIsUnavailableWhenNotConfigured() throws Exception {
        mockMvc.perform(post("/internal/demo-reset/transactions")
                        .header("X-Demo-Reset-Secret", "anything-at-all"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void resetShowtimesIsUnavailableWhenNotConfigured() throws Exception {
        mockMvc.perform(post("/internal/demo-reset/showtimes"))
                .andExpect(status().isServiceUnavailable());
    }
}
