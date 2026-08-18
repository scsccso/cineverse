package com.cineverse.backend.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.cinema.entity.Cinema;
import com.cineverse.backend.cinema.entity.Hall;
import com.cineverse.backend.demo.config.DemoResetProperties;
import com.cineverse.backend.movie.entity.Movie;
import com.cineverse.backend.movie.entity.MovieStatus;
import com.cineverse.backend.movie.repository.MovieRepository;
import com.cineverse.backend.payment.repository.PaymentRepository;
import com.cineverse.backend.showtime.dto.CreateShowtimeRequest;
import com.cineverse.backend.showtime.repository.ShowtimeRepository;
import com.cineverse.backend.showtime.service.ShowtimeService;
import com.cineverse.backend.cinema.repository.HallRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pure-Mockito coverage for verifySecret's branching (the entire access
 * control for a data-destructive endpoint) and the showtime-regeneration
 * count formula. Real end-to-end behavior against a real database/Redis —
 * does the data actually get cleared, do the generated showtimes actually
 * land on the expected dates — lives in DemoResetFlowIntegrationTest
 * instead, same split of responsibility as MovieServiceTest/
 * MovieAdminFlowIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class DemoResetServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private ShowtimeRepository showtimeRepository;

    @Mock
    private ShowtimeService showtimeService;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private HallRepository hallRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    private DemoResetService demoResetServiceWithSecret;

    @BeforeEach
    void setUp() {
        demoResetServiceWithSecret = new DemoResetService(
                new DemoResetProperties("correct-secret"), paymentRepository, bookingRepository,
                showtimeRepository, showtimeService, movieRepository, hallRepository, redisTemplate);
    }

    @Test
    void verifySecretRejectsWithServiceUnavailableWhenNotConfigured() {
        DemoResetService unconfigured = new DemoResetService(
                new DemoResetProperties(null), paymentRepository, bookingRepository,
                showtimeRepository, showtimeService, movieRepository, hallRepository, redisTemplate);

        assertThatThrownBy(() -> unconfigured.verifySecret("anything"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503");
    }

    @Test
    void verifySecretRejectsWithServiceUnavailableWhenConfiguredButBlank() {
        DemoResetService blankConfigured = new DemoResetService(
                new DemoResetProperties("   "), paymentRepository, bookingRepository,
                showtimeRepository, showtimeService, movieRepository, hallRepository, redisTemplate);

        assertThatThrownBy(() -> blankConfigured.verifySecret("anything"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503");
    }

    @Test
    void verifySecretRejectsWrongHeaderWithUnauthorized() {
        assertThatThrownBy(() -> demoResetServiceWithSecret.verifySecret("wrong-secret"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void verifySecretRejectsMissingHeaderWithUnauthorized() {
        assertThatThrownBy(() -> demoResetServiceWithSecret.verifySecret(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    void verifySecretAcceptsTheCorrectHeader() {
        assertThatCode(() -> demoResetServiceWithSecret.verifySecret("correct-secret")).doesNotThrowAnyException();
    }

    @Test
    void resetTransactionsClearsBookingsAndPaymentsAndSeatLocksButNeverTouchesShowtimes() {
        when(paymentRepository.count()).thenReturn(2L);
        when(bookingRepository.count()).thenReturn(5L);
        when(redisTemplate.keys("seat-lock:*")).thenReturn(Set.of("seat-lock:a:b"));

        var result = demoResetServiceWithSecret.resetTransactions();

        verify(paymentRepository).deleteAllInBatch();
        verify(bookingRepository).deleteAllInBatch();
        verify(redisTemplate).delete(Set.of("seat-lock:a:b"));
        verify(showtimeRepository, never()).deleteAllInBatch();
        verify(showtimeService, never()).create(any());
        assertThat(result.bookingsCleared()).isEqualTo(5);
        assertThat(result.paymentsCleared()).isEqualTo(2);
        assertThat(result.showtimesDeleted()).isZero();
        assertThat(result.showtimesCreated()).isZero();
    }

    @Test
    void resetTransactionsSkipsRedisDeleteWhenNoSeatLockKeysExist() {
        when(paymentRepository.count()).thenReturn(0L);
        when(bookingRepository.count()).thenReturn(0L);
        when(redisTemplate.keys("seat-lock:*")).thenReturn(Set.of());

        demoResetServiceWithSecret.resetTransactions();

        verify(redisTemplate, never()).delete(anySet());
    }

    @Test
    void resetShowtimesClearsTransactionalDataThenDeletesAndRegeneratesShowtimes() {
        when(paymentRepository.count()).thenReturn(1L);
        when(bookingRepository.count()).thenReturn(3L);
        when(showtimeRepository.count()).thenReturn(9L);
        when(redisTemplate.keys("seat-lock:*")).thenReturn(Set.of());
        when(movieRepository.findAll(any(Specification.class), eq(Sort.by("title")))).thenReturn(List.of());

        var result = demoResetServiceWithSecret.resetShowtimes();

        verify(paymentRepository).deleteAllInBatch();
        verify(bookingRepository).deleteAllInBatch();
        verify(showtimeRepository).deleteAllInBatch();
        assertThat(result.bookingsCleared()).isEqualTo(3);
        assertThat(result.paymentsCleared()).isEqualTo(1);
        assertThat(result.showtimesDeleted()).isEqualTo(9);
        assertThat(result.showtimesCreated()).isZero();
    }

    @Test
    void regenerateShowtimesCreatesNothingWhenNoMovieIsNowPlaying() {
        when(paymentRepository.count()).thenReturn(0L);
        when(bookingRepository.count()).thenReturn(0L);
        when(showtimeRepository.count()).thenReturn(0L);
        when(redisTemplate.keys("seat-lock:*")).thenReturn(Set.of());
        when(movieRepository.findAll(any(Specification.class), eq(Sort.by("title")))).thenReturn(List.of());

        var result = demoResetServiceWithSecret.resetShowtimes();

        verify(showtimeService, never()).create(any());
        verify(hallRepository, never()).findAll(any(Sort.class));
        assertThat(result.showtimesCreated()).isZero();
    }

    @Test
    void regenerateShowtimesCreatesExactlyHallsTimesThreeSlotsTimesSevenDaysShowtimesRoundRobiningMovies() {
        when(paymentRepository.count()).thenReturn(0L);
        when(bookingRepository.count()).thenReturn(0L);
        when(showtimeRepository.count()).thenReturn(0L);
        when(redisTemplate.keys("seat-lock:*")).thenReturn(Set.of());

        Movie movieA = movie("Movie A");
        Movie movieB = movie("Movie B");
        when(movieRepository.findAll(any(Specification.class), eq(Sort.by("title"))))
                .thenReturn(List.of(movieA, movieB));

        Hall hall1 = hall("Hall 1");
        Hall hall2 = hall("Hall 2");
        Hall hall3 = hall("Hall 3");
        when(hallRepository.findAll(eq(Sort.by("name")))).thenReturn(List.of(hall1, hall2, hall3));

        var result = demoResetServiceWithSecret.resetShowtimes();

        int expected = 3 /* halls */ * 3 /* slots/day */ * 7 /* days */;
        assertThat(result.showtimesCreated()).isEqualTo(expected);

        ArgumentCaptor<CreateShowtimeRequest> captor = ArgumentCaptor.forClass(CreateShowtimeRequest.class);
        verify(showtimeService, times(expected)).create(captor.capture());
        List<CreateShowtimeRequest> requests = captor.getAllValues();

        // Round-robins through exactly the two NOW_PLAYING movies, nothing else.
        Set<UUID> movieIdsUsed = requests.stream().map(CreateShowtimeRequest::movieId).collect(java.util.stream.Collectors.toSet());
        assertThat(movieIdsUsed).containsExactlyInAnyOrder(movieA.getId(), movieB.getId());
        // Every hall actually gets showtimes, not just the first one.
        Set<UUID> hallIdsUsed = requests.stream().map(CreateShowtimeRequest::hallId).collect(java.util.stream.Collectors.toSet());
        assertThat(hallIdsUsed).containsExactlyInAnyOrder(hall1.getId(), hall2.getId(), hall3.getId());
        // Fixed price, matching this codebase's existing test/demo convention.
        assertThat(requests).allSatisfy(r -> assertThat(r.price()).isEqualByComparingTo("25.00"));
    }

    private static Movie movie(String title) {
        Movie movie = new Movie();
        movie.setTitle(title);
        movie.setDurationMinutes(120);
        movie.setStatus(MovieStatus.NOW_PLAYING);
        ReflectionTestUtils.setField(movie, "id", UUID.randomUUID());
        return movie;
    }

    private static Hall hall(String name) {
        Cinema cinema = new Cinema("CineVerse Downtown", "1 Main St");
        Hall hall = new Hall(cinema, name, 6, 10);
        ReflectionTestUtils.setField(hall, "id", UUID.randomUUID());
        return hall;
    }
}
