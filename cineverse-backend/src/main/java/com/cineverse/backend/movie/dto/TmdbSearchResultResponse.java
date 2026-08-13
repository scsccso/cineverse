package com.cineverse.backend.movie.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Deliberately minimal — just enough for an admin to visually pick the
 * right movie out of a results list (title/year/thumbnail). Overview and
 * runtime aren't included here; those only cost a TMDB call once a specific
 * result is selected, see TmdbMovieDetailResponse. */
public record TmdbSearchResultResponse(
        @Schema(example = "157336") long tmdbId,
        @Schema(example = "Interstellar") String title,
        @Schema(example = "2014", description = "Null if TMDB has no release date on file") String releaseYear,
        @Schema(description = "Null if TMDB has no poster for this title") String posterUrl) {
}
