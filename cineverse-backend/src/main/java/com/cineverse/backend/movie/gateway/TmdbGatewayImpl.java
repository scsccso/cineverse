package com.cineverse.backend.movie.gateway;

import com.cineverse.backend.movie.config.TmdbProperties;
import com.cineverse.backend.movie.exception.TmdbGatewayException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Real TMDB API implementation. v3 {@code api_key} query-param auth
 * (not the v4 Bearer read-access-token scheme) — matches what the seed-data
 * population already used, see CLAUDE.md's TMDB backdrop-source record.
 */
@Slf4j
@Component
public class TmdbGatewayImpl implements TmdbGateway {

    private static final String BASE_URL = "https://api.themoviedb.org/3";

    private final TmdbProperties properties;
    private final RestClient restClient;

    public TmdbGatewayImpl(TmdbProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
        // Presence/length only — never the value itself, same reasoning as
        // StripeCheckoutGatewayImpl's constructor log line.
        log.info("TMDB config loaded: apiKey present={} (length={})",
                !properties.apiKey().isBlank(), properties.apiKey().length());
    }

    @Override
    public List<TmdbSearchResult> search(String query) {
        requireApiKey();
        try {
            TmdbSearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/movie")
                            .queryParam("api_key", properties.apiKey())
                            .queryParam("query", query)
                            .build())
                    .retrieve()
                    .body(TmdbSearchResponse.class);
            return response != null && response.results() != null ? response.results() : List.of();
        } catch (RestClientException e) {
            throw new TmdbGatewayException("TMDB search request failed", e);
        }
    }

    @Override
    public TmdbMovieDetails getDetails(long tmdbId) {
        requireApiKey();
        try {
            TmdbMovieDetails details = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/movie/{id}")
                            .queryParam("api_key", properties.apiKey())
                            .queryParam("append_to_response", "videos")
                            .build(tmdbId))
                    .retrieve()
                    .body(TmdbMovieDetails.class);
            if (details == null) {
                throw new TmdbGatewayException("TMDB returned an empty response for movie " + tmdbId);
            }
            return details;
        } catch (RestClientException e) {
            throw new TmdbGatewayException("TMDB detail request failed for movie " + tmdbId, e);
        }
    }

    /** Checked locally before any network call — see TmdbProperties' doc
     * comment for why this doesn't just let TMDB's API reject an empty key
     * remotely the way StripeCheckoutGatewayImpl does. */
    private void requireApiKey() {
        if (properties.apiKey().isBlank()) {
            throw new TmdbGatewayException("TMDB_API_KEY is not configured");
        }
    }
}
