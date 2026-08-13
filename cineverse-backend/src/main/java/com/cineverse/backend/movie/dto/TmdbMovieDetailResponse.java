package com.cineverse.backend.movie.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Prefill data for the create form. Deliberately does not include
 * contentRating/userRating/status/genreIds — TMDB's genre taxonomy and
 * rating systems don't map cleanly onto this project's fixed 15-value genre
 * list or MPAA-style content_rating (see CLAUDE.md's seed-data genre
 * mapping notes), so those stay admin-filled regardless of how the form got
 * pre-populated. */
public record TmdbMovieDetailResponse(
        long tmdbId,
        String title,
        @Schema(description = "TMDB's overview, prefills the description field verbatim") String description,
        @Schema(example = "169") Integer durationMinutes,
        @Schema(description = "https://www.youtube.com/watch?v=... for the preferred trailer, or null if TMDB has none") String trailerUrl,
        @Schema(description = "Hotlinked TMDB image URL, never re-hosted") String posterUrl,
        @Schema(description = "Hotlinked TMDB image URL, never re-hosted") String backdropUrl) {
}
