package com.cineverse.backend.movie.gateway;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cineverse.backend.movie.config.TmdbProperties;
import com.cineverse.backend.movie.exception.TmdbGatewayException;
import org.junit.jupiter.api.Test;

/**
 * A blank/missing TMDB_API_KEY must fail locally and immediately, not by
 * letting TMDB's API reject an empty key remotely (see TmdbProperties'
 * javadoc for why this differs from StripeCheckoutGatewayImpl). No mocking
 * needed — RestClient.builder().build() doesn't connect eagerly, so
 * constructing a real TmdbGatewayImpl here is cheap and exercises the real
 * pre-flight check.
 */
class TmdbGatewayImplTest {

    @Test
    void searchWithBlankApiKeyFailsLocallyWithoutAttemptingANetworkCall() {
        TmdbGatewayImpl gateway = new TmdbGatewayImpl(new TmdbProperties(""));

        assertThatThrownBy(() -> gateway.search("Interstellar"))
                .isInstanceOf(TmdbGatewayException.class)
                .hasMessage("TMDB_API_KEY is not configured");
    }

    @Test
    void getDetailsWithBlankApiKeyFailsLocallyWithoutAttemptingANetworkCall() {
        TmdbGatewayImpl gateway = new TmdbGatewayImpl(new TmdbProperties("   "));

        assertThatThrownBy(() -> gateway.getDetails(157336))
                .isInstanceOf(TmdbGatewayException.class)
                .hasMessage("TMDB_API_KEY is not configured");
    }
}
