package com.cineverse.backend.movie.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Raw shape of TMDB's {@code GET /search/movie} — only page 1 is ever
 * requested (see TmdbGatewayImpl), so total_pages/total_results aren't
 * modeled. {@code @JsonIgnoreProperties} because TMDB's real payload has far
 * more fields than this app needs. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbSearchResponse(List<TmdbSearchResult> results) {
}
