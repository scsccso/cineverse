package com.cineverse.backend.movie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cineverse.backend.movie.dto.MovieRequest;
import com.cineverse.backend.movie.dto.MovieResponse;
import com.cineverse.backend.movie.entity.Movie;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.movie.entity.MovieStatusHistory;
import com.cineverse.backend.movie.exception.MovieStatusChangeNotAllowedException;
import com.cineverse.backend.movie.exception.MovieStatusUnchangedException;
import com.cineverse.backend.movie.mapper.MovieMapper;
import com.cineverse.backend.movie.repository.GenreRepository;
import com.cineverse.backend.movie.repository.MovieRepository;
import com.cineverse.backend.movie.repository.MovieStatusHistoryRepository;
import com.cineverse.backend.showtime.repository.ShowtimeRepository;
import com.cineverse.backend.storage.StorageService;
import com.cineverse.backend.user.entity.Role;
import com.cineverse.backend.user.entity.User;
import com.cineverse.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pure-Mockito coverage for the movie_status_history invariants MovieService
 * enforces: PATCH (changeStatus) is the only path that changes status, PUT
 * (update) rejects a status difference outright, and a same-status PATCH is
 * rejected rather than silently no-op'd. Real HTTP round-trips (and the
 * changedByEmail resolution through a real Authentication principal, and
 * the movie_id CASCADE on delete) live in MovieAdminFlowIntegrationTest
 * instead — same split of responsibility as AdminPaymentServiceTest /
 * AdminPaymentFlowIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private MovieMapper movieMapper;

    @Mock
    private StorageService storageService;

    @Mock
    private ShowtimeRepository showtimeRepository;

    @Mock
    private MovieStatusHistoryRepository movieStatusHistoryRepository;

    @Mock
    private UserRepository userRepository;

    private MovieService movieService;

    private final UUID adminId = UUID.randomUUID();
    private final User adminReference = new User("admin@cineverse.local", "hash", Role.ADMIN, "Admin");

    @BeforeEach
    void setUp() {
        movieService = new MovieService(
                movieRepository, genreRepository, movieMapper, storageService, showtimeRepository,
                movieStatusHistoryRepository, userRepository);
    }

    @Test
    void createWritesTheInitialHistoryRowWithNullFromStatus() {
        MovieRequest request = new MovieRequest(
                "Dune", "desc", "tag", 155, "PG-13", null, null, MovieStatus.COMING_SOON, Set.of());
        Movie saved = new Movie();
        UUID movieId = UUID.randomUUID();
        ReflectionTestUtils.setField(saved, "id", movieId);
        saved.setStatus(MovieStatus.COMING_SOON);
        when(movieRepository.saveAndFlush(any(Movie.class))).thenReturn(saved);
        when(userRepository.getReferenceById(adminId)).thenReturn(adminReference);
        MovieResponse expected = dummyResponse(movieId, MovieStatus.COMING_SOON);
        when(movieMapper.toResponse(saved)).thenReturn(expected);

        MovieResponse result = movieService.create(request, adminId);

        assertThat(result).isEqualTo(expected);
        ArgumentCaptor<MovieStatusHistory> captor = ArgumentCaptor.forClass(MovieStatusHistory.class);
        verify(movieStatusHistoryRepository).saveAndFlush(captor.capture());
        MovieStatusHistory history = captor.getValue();
        assertThat(history.getFromStatus()).isNull();
        assertThat(history.getToStatus()).isEqualTo(MovieStatus.COMING_SOON);
        assertThat(history.getMovie()).isSameAs(saved);
        assertThat(history.getChangedBy()).isSameAs(adminReference);
    }

    @Test
    void changeStatusToADifferentValueUpdatesTheMovieAndWritesHistory() {
        UUID movieId = UUID.randomUUID();
        Movie movie = new Movie();
        movie.setStatus(MovieStatus.COMING_SOON);
        when(movieRepository.findById(movieId)).thenReturn(Optional.of(movie));
        when(movieRepository.saveAndFlush(movie)).thenReturn(movie);
        when(userRepository.getReferenceById(adminId)).thenReturn(adminReference);
        MovieResponse expected = dummyResponse(movieId, MovieStatus.NOW_PLAYING);
        when(movieMapper.toResponse(movie)).thenReturn(expected);

        MovieResponse result = movieService.changeStatus(movieId, MovieStatus.NOW_PLAYING, adminId);

        assertThat(result).isEqualTo(expected);
        assertThat(movie.getStatus()).isEqualTo(MovieStatus.NOW_PLAYING);
        ArgumentCaptor<MovieStatusHistory> captor = ArgumentCaptor.forClass(MovieStatusHistory.class);
        verify(movieStatusHistoryRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getFromStatus()).isEqualTo(MovieStatus.COMING_SOON);
        assertThat(captor.getValue().getToStatus()).isEqualTo(MovieStatus.NOW_PLAYING);
        assertThat(captor.getValue().getChangedBy()).isSameAs(adminReference);
    }

    @Test
    void changeStatusToTheSameValueIsRejectedWithoutAnyWrite() {
        UUID movieId = UUID.randomUUID();
        Movie movie = new Movie();
        movie.setStatus(MovieStatus.NOW_PLAYING);
        when(movieRepository.findById(movieId)).thenReturn(Optional.of(movie));

        assertThatThrownBy(() -> movieService.changeStatus(movieId, MovieStatus.NOW_PLAYING, adminId))
                .isInstanceOf(MovieStatusUnchangedException.class)
                .hasMessageContaining("NOW_PLAYING");

        verify(movieRepository, never()).saveAndFlush(any());
        verifyNoInteractions(movieStatusHistoryRepository, userRepository);
    }

    @ParameterizedTest
    @MethodSource("allDistinctStatusPairs")
    void changeStatusAcceptsEveryDistinctTransitionWithNoRestriction(MovieStatus from, MovieStatus to) {
        UUID movieId = UUID.randomUUID();
        Movie movie = new Movie();
        movie.setStatus(from);
        when(movieRepository.findById(movieId)).thenReturn(Optional.of(movie));
        when(movieRepository.saveAndFlush(movie)).thenReturn(movie);
        when(userRepository.getReferenceById(adminId)).thenReturn(adminReference);
        when(movieMapper.toResponse(movie)).thenReturn(dummyResponse(movieId, to));

        movieService.changeStatus(movieId, to, adminId);

        assertThat(movie.getStatus()).isEqualTo(to);
    }

    private static Stream<Arguments> allDistinctStatusPairs() {
        return Arrays.stream(MovieStatus.values())
                .flatMap(from -> Arrays.stream(MovieStatus.values())
                        .filter(to -> to != from)
                        .map(to -> Arguments.of(from, to)));
    }

    @Test
    void updateRejectsAStatusDifferentFromCurrentWithoutMutatingTheEntity() {
        UUID movieId = UUID.randomUUID();
        Movie movie = new Movie();
        movie.setTitle("Old Title");
        movie.setStatus(MovieStatus.NOW_PLAYING);
        when(movieRepository.findById(movieId)).thenReturn(Optional.of(movie));

        MovieRequest request = new MovieRequest(
                "New Title", null, null, 100, null, null, null, MovieStatus.ENDED, Set.of());

        assertThatThrownBy(() -> movieService.update(movieId, request))
                .isInstanceOf(MovieStatusChangeNotAllowedException.class);

        assertThat(movie.getStatus()).isEqualTo(MovieStatus.NOW_PLAYING);
        assertThat(movie.getTitle()).isEqualTo("Old Title");
        verify(movieRepository, never()).saveAndFlush(any());
        verifyNoInteractions(movieStatusHistoryRepository);
    }

    @Test
    void updateWithUnchangedStatusSucceedsWithoutWritingHistory() {
        UUID movieId = UUID.randomUUID();
        Movie movie = new Movie();
        movie.setStatus(MovieStatus.NOW_PLAYING);
        when(movieRepository.findById(movieId)).thenReturn(Optional.of(movie));
        when(movieRepository.saveAndFlush(movie)).thenReturn(movie);
        MovieResponse expected = dummyResponse(movieId, MovieStatus.NOW_PLAYING);
        when(movieMapper.toResponse(movie)).thenReturn(expected);

        MovieRequest request = new MovieRequest(
                "New Title", null, null, 100, null, null, null, MovieStatus.NOW_PLAYING, Set.of());

        MovieResponse result = movieService.update(movieId, request);

        assertThat(result).isEqualTo(expected);
        assertThat(movie.getTitle()).isEqualTo("New Title");
        verify(movieRepository).saveAndFlush(movie);
        verifyNoInteractions(movieStatusHistoryRepository);
    }

    private MovieResponse dummyResponse(UUID id, MovieStatus status) {
        return new MovieResponse(
                id, "title", null, null, 100, null, null,
                "/images/no-poster.svg", "/images/no-backdrop.svg", null,
                status, List.of(), Instant.now(), Instant.now());
    }
}
