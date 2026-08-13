package com.cineverse.backend.movie.gateway;

import java.util.List;

/**
 * Wraps the two TMDB calls this app makes — deliberately the seam
 * AdminMovieTmdbService depends on, so tests can substitute a fake
 * implementation without a real TMDB API key or live network access, same
 * reasoning as StripeCheckoutGateway wrapping only the Stripe calls that
 * need a real account.
 */
public interface TmdbGateway {

    /** TMDB's {@code /search/movie}, page 1 only — see TmdbGatewayImpl. */
    List<TmdbSearchResult> search(String query);

    /** TMDB's {@code /movie/{id}} with videos appended in the same call. */
    TmdbMovieDetails getDetails(long tmdbId);
}
