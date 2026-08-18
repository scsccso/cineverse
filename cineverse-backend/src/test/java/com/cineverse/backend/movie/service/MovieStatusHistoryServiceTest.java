package com.cineverse.backend.movie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cineverse.backend.movie.dto.MovieStatusHistoryEntryResponse;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.movie.entity.MovieStatusHistory;
import com.cineverse.backend.movie.mapper.MovieStatusHistoryMapper;
import com.cineverse.backend.movie.repository.MovieRepository;
import com.cineverse.backend.movie.repository.MovieStatusHistoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/** Unit coverage for GET /api/v1/admin/movies/{id}/status-history's one
 * real branch (404 short-circuit for an unknown movie) plus the pass-through
 * to the mapper. The 401/403 gate and real newest-first ordering live in
 * MovieAdminFlowIntegrationTest instead. */
@ExtendWith(MockitoExtension.class)
class MovieStatusHistoryServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private MovieStatusHistoryRepository movieStatusHistoryRepository;

    @Mock
    private MovieStatusHistoryMapper movieStatusHistoryMapper;

    private MovieStatusHistoryService movieStatusHistoryService;

    @BeforeEach
    void setUp() {
        movieStatusHistoryService = new MovieStatusHistoryService(
                movieRepository, movieStatusHistoryRepository, movieStatusHistoryMapper);
    }

    @Test
    void unknownMovieIsRejectedWith404BeforeTouchingTheHistoryRepository() {
        UUID movieId = UUID.randomUUID();
        when(movieRepository.existsById(movieId)).thenReturn(false);

        assertThatThrownBy(() -> movieStatusHistoryService.getHistory(movieId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        verifyNoInteractions(movieStatusHistoryRepository, movieStatusHistoryMapper);
    }

    @Test
    void returnsTheMappedHistoryForAKnownMovie() {
        UUID movieId = UUID.randomUUID();
        when(movieRepository.existsById(movieId)).thenReturn(true);
        List<MovieStatusHistory> rows = List.of(new MovieStatusHistory());
        when(movieStatusHistoryRepository.findByMovieIdOrderByChangedAtDesc(movieId)).thenReturn(rows);
        List<MovieStatusHistoryEntryResponse> expected = List.of(new MovieStatusHistoryEntryResponse(
                UUID.randomUUID(), null, MovieStatus.COMING_SOON, Instant.now(), null));
        when(movieStatusHistoryMapper.toResponseList(rows)).thenReturn(expected);

        List<MovieStatusHistoryEntryResponse> result = movieStatusHistoryService.getHistory(movieId);

        assertThat(result).isEqualTo(expected);
    }
}
