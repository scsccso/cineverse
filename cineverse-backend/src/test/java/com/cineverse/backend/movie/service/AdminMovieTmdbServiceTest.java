package com.cineverse.backend.movie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cineverse.backend.movie.dto.TmdbMovieDetailResponse;
import com.cineverse.backend.movie.dto.TmdbSearchResultResponse;
import com.cineverse.backend.movie.gateway.TmdbGateway;
import com.cineverse.backend.movie.gateway.TmdbMovieDetails;
import com.cineverse.backend.movie.gateway.TmdbMovieDetails.TmdbVideo;
import com.cineverse.backend.movie.gateway.TmdbMovieDetails.TmdbVideosWrapper;
import com.cineverse.backend.movie.gateway.TmdbSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure mapping-logic coverage — no Spring context, no Testcontainers, only a
 * mocked TmdbGateway. This is what the task asked for as "unit tests
 * covering the TMDB response -> internal DTO conversion" specifically:
 * trailer selection, image URL construction, and the edge cases TMDB's real
 * data actually has (missing release date, missing poster, no trailer).
 */
class AdminMovieTmdbServiceTest {

    private final TmdbGateway gateway = mock(TmdbGateway.class);
    private final AdminMovieTmdbService service = new AdminMovieTmdbService(gateway);

    @Test
    void searchMapsToSlimResponseWithThumbnailSizedPoster() {
        when(gateway.search("Interstellar")).thenReturn(List.of(
                new TmdbSearchResult(157336, "Interstellar", "2014-11-05", "/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg")));

        List<TmdbSearchResultResponse> results = service.search("Interstellar");

        assertThat(results).hasSize(1);
        TmdbSearchResultResponse result = results.get(0);
        assertThat(result.tmdbId()).isEqualTo(157336);
        assertThat(result.title()).isEqualTo("Interstellar");
        assertThat(result.releaseYear()).isEqualTo("2014");
        assertThat(result.posterUrl()).isEqualTo("https://image.tmdb.org/t/p/w185/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg");
    }

    @Test
    void searchHandlesMissingReleaseDateAndPosterWithoutThrowing() {
        when(gateway.search("Unreleased Movie"))
                .thenReturn(List.of(new TmdbSearchResult(999, "Unreleased Movie", null, null)));

        TmdbSearchResultResponse result = service.search("Unreleased Movie").get(0);

        assertThat(result.releaseYear()).isNull();
        assertThat(result.posterUrl()).isNull();
    }

    @Test
    void searchTreatsBlankReleaseDateAndPosterPathAsAbsent() {
        when(gateway.search("Blank Fields")).thenReturn(List.of(new TmdbSearchResult(1, "Blank Fields", "", "")));

        TmdbSearchResultResponse result = service.search("Blank Fields").get(0);

        assertThat(result.releaseYear()).isNull();
        assertThat(result.posterUrl()).isNull();
    }

    @Test
    void detailsMapsCoreFieldsWithFullSizedImages() {
        when(gateway.getDetails(157336)).thenReturn(new TmdbMovieDetails(
                157336, "Interstellar",
                "A team of explorers travel through a wormhole in space.",
                169, "2014-11-05",
                "/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg", "/xJHokMbljvjADYdit5fK5VQsXEG.jpg",
                new TmdbVideosWrapper(List.of())));

        TmdbMovieDetailResponse response = service.getDetails(157336);

        assertThat(response.tmdbId()).isEqualTo(157336);
        assertThat(response.title()).isEqualTo("Interstellar");
        assertThat(response.description()).isEqualTo("A team of explorers travel through a wormhole in space.");
        assertThat(response.durationMinutes()).isEqualTo(169);
        assertThat(response.posterUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg");
        assertThat(response.backdropUrl())
                .isEqualTo("https://image.tmdb.org/t/p/w1280/xJHokMbljvjADYdit5fK5VQsXEG.jpg");
    }

    @Test
    void detailsPrefersOfficialYoutubeTrailerOverNonOfficialOne() {
        when(gateway.getDetails(1)).thenReturn(detailsWithVideos(List.of(
                new TmdbVideo("nonOfficialKey", "YouTube", "Trailer", false),
                new TmdbVideo("officialKey", "YouTube", "Trailer", true))));

        String trailerUrl = service.getDetails(1).trailerUrl();

        assertThat(trailerUrl).isEqualTo("https://www.youtube.com/watch?v=officialKey");
    }

    @Test
    void detailsFallsBackToNonOfficialYoutubeTrailerIfNoOfficialOneExists() {
        when(gateway.getDetails(1)).thenReturn(
                detailsWithVideos(List.of(new TmdbVideo("onlyKey", "YouTube", "Trailer", false))));

        assertThat(service.getDetails(1).trailerUrl()).isEqualTo("https://www.youtube.com/watch?v=onlyKey");
    }

    @Test
    void detailsIgnoresNonTrailerAndNonYoutubeVideosAndReturnsNullTrailerUrl() {
        when(gateway.getDetails(1)).thenReturn(detailsWithVideos(List.of(
                new TmdbVideo("teaserKey", "YouTube", "Teaser", true),
                new TmdbVideo("vimeoKey", "Vimeo", "Trailer", true))));

        assertThat(service.getDetails(1).trailerUrl()).isNull();
    }

    @Test
    void detailsReturnsNullTrailerUrlWhenVideosAreAbsent() {
        when(gateway.getDetails(1)).thenReturn(new TmdbMovieDetails(
                1, "No Videos Field", "overview", 100, "2020-01-01", null, null, null));

        assertThat(service.getDetails(1).trailerUrl()).isNull();
    }

    private TmdbMovieDetails detailsWithVideos(List<TmdbVideo> videos) {
        return new TmdbMovieDetails(
                1, "Title", "overview", 100, "2020-01-01", null, null, new TmdbVideosWrapper(videos));
    }
}
