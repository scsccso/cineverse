package com.cineverse.backend.movie.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Raw shape of TMDB's {@code GET /movie/{id}?append_to_response=videos} —
 * videos are fetched in this same call (not a separate request) specifically
 * to keep the detail fetch at one TMDB call, not two. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbMovieDetails(
        long id,
        String title,
        String overview,
        Integer runtime,
        @JsonProperty("release_date") String releaseDate,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("backdrop_path") String backdropPath,
        TmdbVideosWrapper videos) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TmdbVideosWrapper(java.util.List<TmdbVideo> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TmdbVideo(String key, String site, String type, boolean official) {
    }
}
