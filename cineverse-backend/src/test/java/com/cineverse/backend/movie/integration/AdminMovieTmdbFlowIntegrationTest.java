package com.cineverse.backend.movie.integration;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cineverse.backend.auth.dto.LoginRequest;
import com.cineverse.backend.auth.dto.RegisterRequest;
import com.cineverse.backend.movie.exception.TmdbGatewayException;
import com.cineverse.backend.movie.gateway.TmdbGateway;
import com.cineverse.backend.movie.gateway.TmdbMovieDetails;
import com.cineverse.backend.movie.gateway.TmdbMovieDetails.TmdbVideosWrapper;
import com.cineverse.backend.movie.gateway.TmdbSearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Only TmdbGateway is mocked — the one seam that needs a real TMDB API key
 * and live network access, same reasoning PaymentFlowIntegrationTest uses
 * for StripeCheckoutGateway. Proves the things AdminMovieTmdbServiceTest's
 * mocked-gateway unit coverage can't: that the controller is wired to
 * ADMIN-only URL-level protection, and that a gateway failure surfaces as a
 * clean 502 through the real GlobalExceptionHandler chain, not a raw 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AdminMovieTmdbFlowIntegrationTest {

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

    @MockitoBean
    private TmdbGateway tmdbGateway;

    @Test
    void tmdbSearchAndDetailEndpointsAreAdminOnly() throws Exception {
        String customerToken = registerAndLoginCustomer();

        mockMvc.perform(get("/api/v1/admin/movies/tmdb-search").param("query", "Interstellar"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/movies/tmdb-search")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .param("query", "Interstellar"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/movies/tmdb-search/157336"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/movies/tmdb-search/157336")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSearchReturnsGatewayResultsMappedToTheSlimResponseShape() throws Exception {
        String adminToken = loginAsAdmin();
        when(tmdbGateway.search(eq("Interstellar"))).thenReturn(List.of(
                new TmdbSearchResult(157336, "Interstellar", "2014-11-05", "/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg")));

        mockMvc.perform(get("/api/v1/admin/movies/tmdb-search")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("query", "Interstellar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tmdbId").value(157336))
                .andExpect(jsonPath("$[0].title").value("Interstellar"))
                .andExpect(jsonPath("$[0].releaseYear").value("2014"))
                .andExpect(jsonPath("$[0].posterUrl")
                        .value("https://image.tmdb.org/t/p/w185/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg"));
    }

    @Test
    void adminDetailFetchReturnsGatewayResultMappedToTheFullResponseShape() throws Exception {
        String adminToken = loginAsAdmin();
        when(tmdbGateway.getDetails(157336)).thenReturn(new TmdbMovieDetails(
                157336, "Interstellar", "A team of explorers travel through a wormhole in space.",
                169, "2014-11-05",
                "/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg", "/xJHokMbljvjADYdit5fK5VQsXEG.jpg",
                new TmdbVideosWrapper(List.of())));

        mockMvc.perform(get("/api/v1/admin/movies/tmdb-search/157336")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tmdbId").value(157336))
                .andExpect(jsonPath("$.description")
                        .value("A team of explorers travel through a wormhole in space."))
                .andExpect(jsonPath("$.durationMinutes").value(169))
                .andExpect(jsonPath("$.posterUrl")
                        .value("https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg"))
                .andExpect(jsonPath("$.backdropUrl")
                        .value("https://image.tmdb.org/t/p/w1280/xJHokMbljvjADYdit5fK5VQsXEG.jpg"))
                .andExpect(jsonPath("$.trailerUrl").doesNotExist());
    }

    @Test
    void searchFailureReturnsACleanBadGatewayNotARawFiveHundred() throws Exception {
        String adminToken = loginAsAdmin();
        when(tmdbGateway.search(eq("anything"))).thenThrow(new TmdbGatewayException("TMDB search request failed"));

        mockMvc.perform(get("/api/v1/admin/movies/tmdb-search")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("query", "anything"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(502))
                .andExpect(jsonPath("$.message").value("TMDB search is temporarily unavailable"));
    }

    @Test
    void notConfiguredGatewayFailureAlsoReturnsACleanBadGateway() throws Exception {
        String adminToken = loginAsAdmin();
        when(tmdbGateway.getDetails(1)).thenThrow(new TmdbGatewayException("TMDB_API_KEY is not configured"));

        mockMvc.perform(get("/api/v1/admin/movies/tmdb-search/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("TMDB search is temporarily unavailable"));
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

    private String registerAndLoginCustomer() throws Exception {
        String email = "tmdb-flow-" + UUID.randomUUID() + "@cineverse.local";
        String password = "Sup3rSecret!";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, password, "TMDB Flow Customer"))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
