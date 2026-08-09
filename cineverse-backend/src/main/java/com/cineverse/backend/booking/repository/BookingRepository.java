package com.cineverse.backend.booking.repository;

import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.entity.BookingStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    List<Booking> findByShowtimeIdAndStatusIn(UUID showtimeId, Collection<BookingStatus> statuses);

    /**
     * One user's whole order history, newest first. Scoped by user_id in the
     * query itself rather than fetched-then-filtered, so there is no code path
     * that can accidentally return someone else's rows.
     *
     * <p>The fetch joins are load-bearing, not an optimisation flourish:
     * Booking.showtime, Showtime.movie and Showtime.hall are all
     * {@code FetchType.LAZY}, and BookingMapper reads the movie title and hall
     * name for every row — without them a 20-order history costs 60 extra
     * round trips. Derived-query naming can't express this, hence the explicit
     * {@code @Query}.
     */
    @Query("""
            select b from Booking b
            join fetch b.showtime s
            join fetch s.movie
            join fetch s.hall
            where b.user.id = :userId
            order by b.createdAt desc
            """)
    List<Booking> findAllByUserIdNewestFirst(@Param("userId") UUID userId);

    // Backs ShowtimeService.delete()'s guard — deleting a showtime must not
    // orphan/cascade-fail its booking history, same reasoning as
    // MovieService.delete()'s existsByMovieId (see V8/V9).
    boolean existsByShowtimeId(UUID showtimeId);
}
