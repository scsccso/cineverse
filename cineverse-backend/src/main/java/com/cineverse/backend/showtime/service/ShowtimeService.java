package com.cineverse.backend.showtime.service;

import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.cinema.entity.Hall;
import com.cineverse.backend.cinema.repository.HallRepository;
import com.cineverse.backend.movie.entity.Movie;
import com.cineverse.backend.movie.repository.MovieRepository;
import com.cineverse.backend.showtime.dto.CreateShowtimeRequest;
import com.cineverse.backend.showtime.dto.ShowtimeResponse;
import com.cineverse.backend.showtime.entity.Showtime;
import com.cineverse.backend.showtime.exception.ShowtimeConflictException;
import com.cineverse.backend.showtime.exception.ShowtimeHasBookingsException;
import com.cineverse.backend.showtime.mapper.ShowtimeMapper;
import com.cineverse.backend.showtime.repository.ShowtimeRepository;
import com.cineverse.backend.showtime.repository.ShowtimeSpecifications;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShowtimeService {

    /** Cleanup time required between the end of one showtime and the start of the next, in the same hall. */
    private static final Duration CLEANUP_BUFFER = Duration.ofMinutes(20);

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final HallRepository hallRepository;
    private final BookingRepository bookingRepository;
    private final ShowtimeMapper showtimeMapper;

    public ShowtimeService(
            ShowtimeRepository showtimeRepository,
            MovieRepository movieRepository,
            HallRepository hallRepository,
            BookingRepository bookingRepository,
            ShowtimeMapper showtimeMapper) {
        this.showtimeRepository = showtimeRepository;
        this.movieRepository = movieRepository;
        this.hallRepository = hallRepository;
        this.bookingRepository = bookingRepository;
        this.showtimeMapper = showtimeMapper;
    }

    @Transactional(readOnly = true)
    public List<ShowtimeResponse> list(UUID movieId, LocalDate date) {
        Specification<Showtime> spec = Specification.where(ShowtimeSpecifications.hasMovie(movieId))
                .and(ShowtimeSpecifications.startsOnDate(date));
        return showtimeMapper.toResponseList(showtimeRepository.findAll(spec));
    }

    @Transactional(readOnly = true)
    public ShowtimeResponse getById(UUID id) {
        return showtimeMapper.toResponse(findShowtimeOrThrow(id));
    }

    @Transactional
    public ShowtimeResponse create(CreateShowtimeRequest request) {
        Movie movie = movieRepository.findById(request.movieId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found"));
        Hall hall = hallRepository.findById(request.hallId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hall not found"));

        Instant startTime = request.startTime();
        Instant endTime = startTime.plus(Duration.ofMinutes(movie.getDurationMinutes()));

        rejectIfConflicting(hall, startTime, endTime);

        Showtime showtime = new Showtime(movie, hall, startTime, endTime, request.price());
        // saveAndFlush: Showtime has @CreationTimestamp/@UpdateTimestamp,
        // and the response must reflect them, not null — see
        // TimestampedEntitySaveFlushRule.
        return showtimeMapper.toResponse(showtimeRepository.saveAndFlush(showtime));
    }

    @Transactional
    public void delete(UUID id) {
        Showtime showtime = findShowtimeOrThrow(id);
        // Checked here (not left to the FK) for a clean 409 instead of a raw
        // DB constraint error — same reasoning as MovieService.delete()'s
        // existsByMovieId check. showtimes.id is ON DELETE RESTRICT from
        // bookings (see V9); this is the primary guard, that's the backstop.
        if (bookingRepository.existsByShowtimeId(id)) {
            throw new ShowtimeHasBookingsException();
        }
        showtimeRepository.delete(showtime);
    }

    private void rejectIfConflicting(Hall hall, Instant startTime, Instant endTime) {
        List<Showtime> existingInHall = showtimeRepository.findByHallId(hall.getId());
        Optional<Showtime> conflict = existingInHall.stream()
                .filter(existing -> ShowtimeOverlapChecker.conflicts(
                        startTime, endTime, existing.getStartTime(), existing.getEndTime(), CLEANUP_BUFFER))
                .findFirst();
        if (conflict.isPresent()) {
            Showtime existing = conflict.get();
            throw new ShowtimeConflictException(hall.getName(), existing.getStartTime(), existing.getEndTime());
        }
    }

    private Showtime findShowtimeOrThrow(UUID id) {
        return showtimeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Showtime not found"));
    }
}
