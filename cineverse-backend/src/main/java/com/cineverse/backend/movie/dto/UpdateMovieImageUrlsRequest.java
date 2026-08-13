package com.cineverse.backend.movie.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Partial update (PATCH, not PUT — matches the same full-replace-vs-patch
 * distinction PATCH /api/v1/admin/users/{id}/role already establishes):
 * either field, if present, is set directly on the movie, bypassing
 * StorageService entirely (this hotlinks an external URL, it never uploads
 * or re-hosts a file) — see MovieService.updateImageUrls. Either field
 * omitted/null leaves that image untouched. No @URL/format validation
 * beyond length, matching trailerUrl's existing looseness in MovieRequest —
 * this is an ADMIN-only, already-trusted-role endpoint. */
public record UpdateMovieImageUrlsRequest(
        @Size(max = 500) @Schema(description = "Hotlinked external URL, e.g. a TMDB image path") String posterUrl,
        @Size(max = 500) @Schema(description = "Hotlinked external URL, e.g. a TMDB image path") String backdropUrl) {
}
