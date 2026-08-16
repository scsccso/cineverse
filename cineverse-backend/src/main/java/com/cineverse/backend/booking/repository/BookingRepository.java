package com.cineverse.backend.booking.repository;

import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.entity.BookingStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // Backs AdminUserService.deleteUser()'s guard - deleting a user must not
    // orphan/cascade-fail its booking history.
    boolean existsByUserId(UUID userId);

    /**
     * Admin support search (GET /api/v1/admin/bookings) — the only way to
     * reach a booking today without already knowing its UUID is
     * findAllByUserIdNewestFirst, and that's deliberately self-scoped (see
     * its own doc comment), so it can't serve "find this customer's order."
     * All three filters are optional and independently combinable; each
     * {@code :param is null or ...} clause is a no-op when its param is
     * null, so calling this with everything null degrades to a plain
     * paginated browse-all, same shape as AdminUserService.getAllUsers.
     *
     * <p>The fetch joins exist for the same reason
     * findAllByUserIdNewestFirst's do: AdminBookingService maps every row
     * through BookingMapper, which reads the movie title and hall name off
     * each one. An explicit countQuery is provided rather than relying on
     * Spring Data to derive one by stripping this query's joins/select —
     * that derivation is not something this codebase leans on elsewhere,
     * and getting it wrong silently produces a wrong total/page count
     * rather than a startup-time error.
     *
     * <p>{@code userEmailPattern}/{@code movieTitlePattern} are the already
     * lower-cased {@code "%...%"} wildcarded strings (built by
     * AdminBookingService), not the raw search term with {@code lower()}/
     * {@code concat()} applied here in JPQL — {@code lower(concat('%', ?,
     * '%'))} looks equivalent but isn't: PostgreSQL's extended query
     * protocol cannot infer a type for a placeholder used only inside that
     * expression and fails at runtime with "function lower(bytea) does not
     * exist" the moment the parameter is non-null (confirmed by
     * AdminBookingFlowIntegrationTest before this was found — every
     * userEmail-filtered case failed with a 500, while the unfiltered and
     * movieTitle-only cases passed). Pre-building the pattern keeps
     * {@code lower(...)} applied only to the real, unambiguously-typed
     * column on the left of {@code like}.
     */
    @Query(
            value = """
                    select b from Booking b
                    join fetch b.user u
                    join fetch b.showtime s
                    join fetch s.movie m
                    join fetch s.hall h
                    where (:userEmailPattern is null or lower(u.email) like :userEmailPattern)
                      and (:movieTitlePattern is null or lower(m.title) like :movieTitlePattern)
                      and (:status is null or b.status = :status)
                    order by b.createdAt desc
                    """,
            countQuery = """
                    select count(b) from Booking b
                    join b.user u
                    join b.showtime s
                    join s.movie m
                    where (:userEmailPattern is null or lower(u.email) like :userEmailPattern)
                      and (:movieTitlePattern is null or lower(m.title) like :movieTitlePattern)
                      and (:status is null or b.status = :status)
                    """)
    Page<Booking> search(
            @Param("userEmailPattern") String userEmailPattern,
            @Param("movieTitlePattern") String movieTitlePattern,
            @Param("status") BookingStatus status,
            Pageable pageable);
}
