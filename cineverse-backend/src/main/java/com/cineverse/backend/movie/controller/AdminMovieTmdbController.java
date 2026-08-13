package com.cineverse.backend.movie.controller;

import com.cineverse.backend.movie.dto.TmdbMovieDetailResponse;
import com.cineverse.backend.movie.dto.TmdbSearchResultResponse;
import com.cineverse.backend.movie.service.AdminMovieTmdbService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-side proxy for TMDB — the API key never reaches the browser, same
 * reasoning as OMDB_API_KEY never being client-side (see CLAUDE.md's
 * seed-data source notes). No {@code @PreAuthorize}: ADMIN-only access is
 * covered by SecurityConfig's URL-level {@code
 * .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")}, same pattern as
 * every other admin controller — see the antigravity.md-recorded lesson
 * about a redundant, silently-inert {@code @PreAuthorize} on
 * AdminUserController.
 */
@RestController
@RequestMapping("/api/v1/admin/movies")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Movies — TMDB Search")
public class AdminMovieTmdbController {

    private final AdminMovieTmdbService adminMovieTmdbService;

    public AdminMovieTmdbController(AdminMovieTmdbService adminMovieTmdbService) {
        this.adminMovieTmdbService = adminMovieTmdbService;
    }

    @GetMapping("/tmdb-search")
    @Operation(summary = "按标题搜索 TMDB", description = "仅 ADMIN;只返回标题/年份/海报缩略图,不含简介/时长——选中某一条后另调详情接口")
    public List<TmdbSearchResultResponse> search(@RequestParam String query) {
        return adminMovieTmdbService.search(query);
    }

    @GetMapping("/tmdb-search/{tmdbId}")
    @Operation(summary = "获取某条 TMDB 搜索结果的完整详情", description = "仅 ADMIN;简介/时长/预告片/海报/背景图,用于预填创建表单")
    public TmdbMovieDetailResponse getDetails(@PathVariable long tmdbId) {
        return adminMovieTmdbService.getDetails(tmdbId);
    }
}
