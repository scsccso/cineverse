package com.cineverse.backend.booking.repository;

import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.entity.BookingStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findByShowtimeIdAndStatusIn(UUID showtimeId, Collection<BookingStatus> statuses);

    // Backs ShowtimeService.delete()'s guard — deleting a showtime must not
    // orphan/cascade-fail its booking history, same reasoning as
    // MovieService.delete()'s existsByMovieId (see V8/V9).
    boolean existsByShowtimeId(UUID showtimeId);
}
