package com.cineverse.backend.movie.controller;

import com.cineverse.backend.movie.dto.MovieStatusHistoryEntryResponse;
import com.cineverse.backend.movie.service.MovieStatusHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kept off /api/v1/movies/** on purpose: that path's GET verbs are publicly
 * permitAll'd (SecurityConfig), and this response includes the acting
 * admin's email — must stay ADMIN-only. /api/v1/admin/** already is, and
 * AdminMovieTmdbController already uses this same base path for a different
 * admin-only movie concern. No {@code @PreAuthorize} — same reasoning as
 * every other admin controller in this project (see AdminMovieTmdbController's
 * doc comment).
 */
@RestController
@RequestMapping("/api/v1/admin/movies")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Movies — Status History")
public class AdminMovieStatusHistoryController {

    private final MovieStatusHistoryService movieStatusHistoryService;

    public AdminMovieStatusHistoryController(MovieStatusHistoryService movieStatusHistoryService) {
        this.movieStatusHistoryService = movieStatusHistoryService;
    }

    @GetMapping("/{id}/status-history")
    @Operation(summary = "查询电影状态变更历史", description = "仅 ADMIN;按 changedAt 倒序(最新在前)。"
            + "第一条记录(fromStatus=null)是电影创建时写入的初始状态,不是一次变更")
    public List<MovieStatusHistoryEntryResponse> getHistory(@PathVariable UUID id) {
        return movieStatusHistoryService.getHistory(id);
    }
}
