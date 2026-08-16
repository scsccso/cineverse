package com.cineverse.backend.movie.integration;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cineverse.backend.auth.dto.LoginRequest;
import com.cineverse.backend.auth.dto.RegisterRequest;
import com.cineverse.backend.movie.dto.MovieRequest;
import com.cineverse.backend.movie.dto.UpdateMovieImageUrlsRequest;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.showtime.dto.CreateShowtimeRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Movie Management against a real Postgres (Testcontainers): public
 * read-only browsing, ADMIN-only mutations (401 for no token — standard
 * Spring Security semantics for an anonymous principal hitting an
 * AccessDeniedException; 403 is reserved for an authenticated-but-wrong-role
 * principal, covered separately below), and the full admin flow
 * create -> upload poster -> verify -> delete -> 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class MovieAdminFlowIntegrationTest {

    private static final String SEEDED_HALL_ID = "21111111-1111-1111-1111-111111111111"; // Hall 1, from V6 seed

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        // Point uploads at a throwaway temp dir instead of the real
        // cineverse-backend/uploads/ so tests never write into the repo.
        registry.add("app.storage.upload-dir",
                () -> System.getProperty("java.io.tmpdir") + "/cineverse-test-uploads-" + UUID.randomUUID());
        registry.add("app.security.cookie-secure", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanCreateUploadPosterAndDeleteMovie() throws Exception {
        String accessToken = loginAsAdmin();
        UUID genreId = firstGenreId();
        String title = "Interstellar " + UUID.randomUUID();

        MovieRequest createRequest = new MovieRequest(
                title, "A team travels through a wormhole.", "Mankind's next step.",
                169, "PG-13", null, null, MovieStatus.NOW_PLAYING, Set.of(genreId));

        MvcResult createResult = mockMvc.perform(post("/api/v1/movies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.posterUrl").value("/images/no-poster.svg"))
                .andExpect(jsonPath("$.genres[0].id").value(genreId.toString()))
                .andReturn();

        UUID movieId = readId(createResult);

        // Public, no auth needed.
        mockMvc.perform(get("/api/v1/movies/{id}", movieId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(title));

        MockMultipartFile poster =
                new MockMultipartFile("file", "poster.jpg", "image/jpeg", "fake-poster-bytes".getBytes());
        mockMvc.perform(multipart("/api/v1/movies/{id}/poster", movieId)
                        .file(poster)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posterUrl").value(startsWith("/uploads/")));

        mockMvc.perform(get("/api/v1/movies/{id}", movieId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posterUrl").value(startsWith("/uploads/")));

        mockMvc.perform(delete("/api/v1/movies/{id}", movieId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/movies/{id}", movieId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingAMovieWithAScheduledShowtimeIsRejectedWith409() throws Exception {
        String accessToken = loginAsAdmin();
        UUID genreId = firstGenreId();
        UUID movieId = createMovie(accessToken, "Booked Solid " + UUID.randomUUID(), genreId);

        mockMvc.perform(post("/api/v1/showtimes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateShowtimeRequest(
                                movieId, UUID.fromString(SEEDED_HALL_ID),
                                Instant.parse("2026-09-10T10:00:00Z"), new BigDecimal("25.00")))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/movies/{id}", movieId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("scheduled showtimes")));

        // Untouched — the rejected delete must not have removed the movie.
        mockMvc.perform(get("/api/v1/movies/{id}", movieId))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousUsersCanBrowseButNotMutateMovies() throws Exception {
        mockMvc.perform(get("/api/v1/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        String accessToken = loginAsAdmin();
        UUID genreId = firstGenreId();
        UUID movieId = createMovie(accessToken, "Anon Guard " + UUID.randomUUID(), genreId);

        MovieRequest anyRequest = new MovieRequest(
                "hacked title", null, null, 100, null, null, null, MovieStatus.ENDED, Set.of());

        // No Authorization header at all: Spring Security treats this as an
        // anonymous principal, and AccessDeniedException for an anonymous
        // principal is translated to 401 (via the entry point) rather than
        // 403 — the framework's standard "you must authenticate first"
        // signal. 403 is reserved for a real, logged-in-but-wrong-role
        // principal; see authenticatedCustomerCannotMutateMovies below.
        mockMvc.perform(post("/api/v1/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(anyRequest)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/movies/{id}", movieId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(anyRequest)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/movies/{id}", movieId))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/movies/{id}/image-urls", movieId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateMovieImageUrlsRequest("https://example.com/p.jpg", null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCustomerCannotMutateMovies() throws Exception {
        String customerToken = registerAndLoginCustomer();
        String adminToken = loginAsAdmin();
        UUID genreId = firstGenreId();
        UUID movieId = createMovie(adminToken, "Role Guard " + UUID.randomUUID(), genreId);

        MovieRequest anyRequest = new MovieRequest(
                "hacked title", null, null, 100, null, null, null, MovieStatus.ENDED, Set.of());

        // A real, authenticated CUSTOMER — this is the genuine 403 case:
        // known principal, just the wrong role.
        mockMvc.perform(post("/api/v1/movies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(anyRequest)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/movies/{id}", movieId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(anyRequest)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/movies/{id}", movieId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/movies/{id}/image-urls", movieId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateMovieImageUrlsRequest("https://example.com/p.jpg", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanSetImageUrlsDirectlyAsAPartialUpdateBypassingUpload() throws Exception {
        String accessToken = loginAsAdmin();
        UUID genreId = firstGenreId();
        UUID movieId = createMovie(accessToken, "TMDB Hotlink " + UUID.randomUUID(), genreId);

        // Only posterUrl set — backdropUrl omitted/null must leave the
        // existing (placeholder) backdrop untouched, matching PATCH's
        // partial-update semantics (not PUT's full replace).
        mockMvc.perform(patch("/api/v1/movies/{id}/image-urls", movieId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateMovieImageUrlsRequest(
                                "https://image.tmdb.org/t/p/w500/poster.jpg", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posterUrl").value("https://image.tmdb.org/t/p/w500/poster.jpg"))
                .andExpect(jsonPath("$.backdropUrl").value("/images/no-backdrop.svg"));

        // A second call setting only backdropUrl must leave the posterUrl
        // set above untouched.
        mockMvc.perform(patch("/api/v1/movies/{id}/image-urls", movieId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateMovieImageUrlsRequest(
                                null, "https://image.tmdb.org/t/p/w1280/backdrop.jpg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posterUrl").value("https://image.tmdb.org/t/p/w500/poster.jpg"))
                .andExpect(jsonPath("$.backdropUrl").value("https://image.tmdb.org/t/p/w1280/backdrop.jpg"));

        mockMvc.perform(get("/api/v1/movies/{id}", movieId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posterUrl").value("https://image.tmdb.org/t/p/w500/poster.jpg"))
                .andExpect(jsonPath("$.backdropUrl").value("https://image.tmdb.org/t/p/w1280/backdrop.jpg"));
    }

    @Test
    void uploadRejectsUnsupportedTypeAndOversizedFile() throws Exception {
        String accessToken = loginAsAdmin();
        UUID genreId = firstGenreId();
        UUID movieId = createMovie(accessToken, "Bad Upload " + UUID.randomUUID(), genreId);

        MockMultipartFile wrongType = new MockMultipartFile("file", "poster.gif", "image/gif", "data".getBytes());
        mockMvc.perform(multipart("/api/v1/movies/{id}/poster", movieId)
                        .file(wrongType)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest());

        byte[] tooBig = new byte[5 * 1024 * 1024 + 1024];
        MockMultipartFile oversized = new MockMultipartFile("file", "poster.png", "image/png", tooBig);
        mockMvc.perform(multipart("/api/v1/movies/{id}/backdrop", movieId)
                        .file(oversized)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void titleSearchIsCaseInsensitiveAndFindsOnlyMatchingMovies() throws Exception {
        String accessToken = loginAsAdmin();
        UUID genreId = firstGenreId();
        String uniqueTitle = "Zzyzx Title Search " + UUID.randomUUID();
        UUID matchingMovieId = createMovie(accessToken, uniqueTitle, genreId);
        // A second, unrelated movie exists in the same run (this one, plus
        // 11 real seed movies) — the assertion below checks this one movie
        // is present and searching an unrelated fragment excludes it,
        // rather than asserting an exact result count against the whole
        // public catalog.
        createMovie(accessToken, "Unrelated Movie " + UUID.randomUUID(), genreId);

        mockMvc.perform(get("/api/v1/movies").param("title", "zzyzx title search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + matchingMovieId + "')]").exists())
                .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/movies").param("title", "a title that matches nothing " + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    private UUID createMovie(String accessToken, String title, UUID genreId) throws Exception {
        MovieRequest request = new MovieRequest(
                title, "desc", "tagline", 120, "PG", null, null, MovieStatus.COMING_SOON, Set.of(genreId));

        MvcResult result = mockMvc.perform(post("/api/v1/movies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return readId(result);
    }

    private UUID firstGenreId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/genres"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode genres = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(genres.get(0).get("id").asText());
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

    private UUID readId(MvcResult result) throws Exception {
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        return UUID.fromString(id);
    }

    private String registerAndLoginCustomer() throws Exception {
        String email = "customer-" + UUID.randomUUID() + "@cineverse.local";
        String password = "Sup3rSecret!";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(email, password, "Role Test Customer"))))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }
}
