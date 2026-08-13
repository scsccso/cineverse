package com.cineverse.backend.movie.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** One hit from TMDB's search results — the raw shape, before
 * AdminMovieTmdbService trims it down to TmdbSearchResultResponse for the
 * frontend. {@code releaseDate} may be blank (TMDB has entries with no
 * confirmed release date); {@code posterPath} may be null. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbSearchResult(
        long id,
        String title,
        @JsonProperty("release_date") String releaseDate,
        @JsonProperty("poster_path") String posterPath) {
}
