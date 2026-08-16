package com.cineverse.backend.booking.service;

import com.cineverse.backend.booking.dto.AdminBookingSearchResult;
import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.entity.BookingSeat;
import com.cineverse.backend.booking.entity.BookingStatus;
import com.cineverse.backend.booking.event.BookingReleasedEvent;
import com.cineverse.backend.booking.mapper.BookingMapper;
import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.booking.repository.BookingSeatRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs GET /api/v1/admin/bookings — customer-support search, since
 * BookingService.listForUser is deliberately self-scoped and there was
 * otherwise no way to reach a booking without already knowing its UUID.
 * Kept as its own service (not a method bolted onto BookingService) because
 * it depends on nothing BookingService doesn't already expose via the
 * repository/mapper it also uses, and every existing "Admin*" service in
 * this codebase (AdminUserService, etc.) is likewise separate from its
 * non-admin counterpart rather than merged into it.
 */
@Service
public class AdminBookingService {

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final BookingMapper bookingMapper;
    private final ApplicationEventPublisher eventPublisher;

    public AdminBookingService(
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            BookingMapper bookingMapper,
            ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.bookingMapper = bookingMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Not readOnly: same reason as BookingService.listForUser — the lazy
     * expiry rule every read path applies (CLAUDE.md Phase 5: no scheduled
     * sweep, each read settles what it sees) is applied here too, batched
     * for the current page rather than per row.
     */
    @Transactional
    public Page<AdminBookingSearchResult> search(
            String userEmail, String movieTitle, BookingStatus status, Pageable pageable) {
        Page<Booking> page =
                bookingRepository.search(likePattern(userEmail), likePattern(movieTitle), status, pageable);

        List<Booking> bookings = page.getContent();
        if (bookings.isEmpty()) {
            // Short-circuit: skips both an "in ()" seat query and the flush
            // below. page.map() over an empty content list never invokes the
            // converter, but still carries over page.getTotalElements() —
            // which can be genuinely nonzero here (the requested page number
            // is past the last page with results), unlike Page.empty(pageable)
            // which would report a total of 0 regardless.
            return page.map(booking -> null);
        }

        Instant now = Instant.now();
        List<Booking> newlyExpired = bookings.stream()
                .filter(booking ->
                        BookingStateMachine.shouldLazilyExpire(booking.getStatus(), booking.getExpiresAt(), now))
                .toList();
        if (!newlyExpired.isEmpty()) {
            newlyExpired.forEach(Booking::markExpired);
            bookingRepository.saveAllAndFlush(newlyExpired);
            newlyExpired.forEach(booking -> eventPublisher.publishEvent(new BookingReleasedEvent(booking.getId())));
        }

        Map<UUID, List<BookingSeat>> seatsByBookingId = bookingSeatRepository
                .findByBookingIdInWithSeat(bookings.stream().map(Booking::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(bookingSeat -> bookingSeat.getBooking().getId()));

        return page.map(booking -> new AdminBookingSearchResult(
                booking.getUser().getEmail(),
                bookingMapper.toResponse(booking, seatsByBookingId.getOrDefault(booking.getId(), List.of()))));
    }

    /**
     * Lower-cased {@code "%term%"} pattern, or null for a blank/absent
     * filter. See BookingRepository.search's doc comment for why this is
     * built here in Java rather than with {@code lower(concat(...))} in
     * JPQL — the latter 500s on PostgreSQL for any non-null value.
     */
    private String likePattern(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : "%" + trimmed.toLowerCase() + "%";
    }
}
