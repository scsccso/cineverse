package com.cineverse.backend.movie.service;

import com.cineverse.backend.movie.dto.TmdbMovieDetailResponse;
import com.cineverse.backend.movie.dto.TmdbSearchResultResponse;
import com.cineverse.backend.movie.gateway.TmdbGateway;
import com.cineverse.backend.movie.gateway.TmdbMovieDetails;
import com.cineverse.backend.movie.gateway.TmdbMovieDetails.TmdbVideo;
import com.cineverse.backend.movie.gateway.TmdbSearchResult;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Maps TMDB's raw response shapes to the slim DTOs the admin frontend
 * actually needs — kept separate from TmdbGatewayImpl so this mapping logic
 * (trailer selection, image URL construction, year extraction) is testable
 * without a real or mocked HTTP call, only a mocked TmdbGateway. */
@Service
public class AdminMovieTmdbService {

    private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p";
    private static final String SEARCH_THUMBNAIL_SIZE = "w185";
    private static final String POSTER_SIZE = "w500";
    private static final String BACKDROP_SIZE = "w1280";

    private final TmdbGateway tmdbGateway;

    public AdminMovieTmdbService(TmdbGateway tmdbGateway) {
        this.tmdbGateway = tmdbGateway;
    }

    public List<TmdbSearchResultResponse> search(String query) {
        return tmdbGateway.search(query).stream().map(this::toSearchResultResponse).toList();
    }

    public TmdbMovieDetailResponse getDetails(long tmdbId) {
        return toDetailResponse(tmdbGateway.getDetails(tmdbId));
    }

    private TmdbSearchResultResponse toSearchResultResponse(TmdbSearchResult result) {
        return new TmdbSearchResultResponse(
                result.id(),
                result.title(),
                releaseYear(result.releaseDate()),
                imageUrl(result.posterPath(), SEARCH_THUMBNAIL_SIZE));
    }

    private TmdbMovieDetailResponse toDetailResponse(TmdbMovieDetails details) {
        return new TmdbMovieDetailResponse(
                details.id(),
                details.title(),
                details.overview(),
                details.runtime(),
                trailerUrl(details),
                imageUrl(details.posterPath(), POSTER_SIZE),
                imageUrl(details.backdropPath(), BACKDROP_SIZE));
    }

    /** Prefer an official YouTube trailer; fall back to any YouTube trailer;
     * null if TMDB has neither (no non-YouTube fallback — a Vimeo link
     * wouldn't match the "YouTube trailer key" URL shape this field
     * promises). */
    private String trailerUrl(TmdbMovieDetails details) {
        if (details.videos() == null || details.videos().results() == null) {
            return null;
        }
        List<TmdbVideo> youtubeTrailers = details.videos().results().stream()
                .filter(video -> "YouTube".equals(video.site()) && "Trailer".equals(video.type()))
                .toList();
        return youtubeTrailers.stream()
                .max(Comparator.comparing(TmdbVideo::official))
                .map(video -> "https://www.youtube.com/watch?v=" + video.key())
                .orElse(null);
    }

    private String releaseYear(String releaseDate) {
        return releaseDate != null && releaseDate.length() >= 4 ? releaseDate.substring(0, 4) : null;
    }

    private String imageUrl(String path, String size) {
        return Optional.ofNullable(path)
                .filter(p -> !p.isBlank())
                .map(p -> IMAGE_BASE_URL + "/" + size + p)
                .orElse(null);
    }
}
