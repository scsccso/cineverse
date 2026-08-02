package com.cineverse.backend.booking.service;

import com.cineverse.backend.booking.dto.BookingResponse;
import com.cineverse.backend.booking.dto.CreateBookingRequest;
import com.cineverse.backend.booking.dto.ShowtimeSeatsResponse;
import com.cineverse.backend.booking.entity.Booking;
import com.cineverse.backend.booking.entity.BookingSeat;
import com.cineverse.backend.booking.entity.BookingStatus;
import com.cineverse.backend.booking.exception.SeatUnavailableException;
import com.cineverse.backend.booking.mapper.BookingMapper;
import com.cineverse.backend.booking.repository.BookingRepository;
import com.cineverse.backend.booking.repository.BookingSeatRepository;
import com.cineverse.backend.cinema.entity.Hall;
import com.cineverse.backend.cinema.entity.Seat;
import com.cineverse.backend.cinema.entity.SeatType;
import com.cineverse.backend.cinema.repository.SeatRepository;
import com.cineverse.backend.showtime.entity.Showtime;
import com.cineverse.backend.showtime.repository.ShowtimeRepository;
import com.cineverse.backend.user.entity.User;
import com.cineverse.backend.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {

    /** Both the Redis lock TTL and the booking's DB expires_at — one constant so the two can never drift apart. */
    static final Duration HOLD_DURATION = Duration.ofMinutes(5);

    private static final List<BookingStatus> ACTIVE_STATUSES =
            List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final UserRepository userRepository;
    private final SeatLockService seatLockService;
    private final BookingMapper bookingMapper;

    public BookingService(
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            ShowtimeRepository showtimeRepository,
            SeatRepository seatRepository,
            UserRepository userRepository,
            SeatLockService seatLockService,
            BookingMapper bookingMapper) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
        this.userRepository = userRepository;
        this.seatLockService = seatLockService;
        this.bookingMapper = bookingMapper;
    }

    /**
     * Not readOnly: activeSeatStatuses() below may lazily persist expired
     * bookings as a side effect of this "read".
     */
    @Transactional
    public ShowtimeSeatsResponse getShowtimeSeats(UUID showtimeId) {
        Showtime showtime = findShowtimeOrThrow(showtimeId);
        Hall hall = showtime.getHall();
        List<Seat> seats = seatRepository.findByHallIdOrderByRowLabelAscColumnNumberAsc(hall.getId());

        Map<UUID, BookingStatus> statusBySeatId = activeSeatStatuses(showtimeId);

        List<ShowtimeSeatsResponse.SeatStatusEntry> entries = seats.stream()
                .map(seat -> new ShowtimeSeatsResponse.SeatStatusEntry(
                        seat.getId(),
                        seat.getRowLabel(),
                        seat.getColumnNumber(),
                        columnSpanFor(seat.getSeatType()),
                        seat.getSeatType(),
                        BookingStateMachine.resolveSeatStatus(statusBySeatId.get(seat.getId()))))
                .toList();

        return new ShowtimeSeatsResponse(
                showtimeId, hall.getId(), hall.getName(), hall.getTotalRows(), hall.getTotalColumns(), entries);
    }

    @Transactional
    public BookingResponse create(UUID userId, CreateBookingRequest request) {
        Showtime showtime = findShowtimeOrThrow(request.showtimeId());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<UUID> seatIds = request.seatIds();
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate seat IDs in request");
        }

        List<Seat> seats = seatRepository.findAllById(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more seat IDs do not exist");
        }
        boolean allSeatsBelongToHall = seats.stream()
                .allMatch(seat -> seat.getHall().getId().equals(showtime.getHall().getId()));
        if (!allSeatsBelongToHall) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "One or more seats do not belong to this showtime's hall");
        }

        // Fast DB-level check first: catches seats that are durably already
        // taken, and is the backstop if Redis ever loses its data (it has no
        // persistence configured — see docker-compose.yml). This check
        // ALONE is not sufficient against concurrent requests, since two
        // transactions can both pass it before either commits — that race
        // is closed by the atomic Redis lock immediately below.
        Map<UUID, BookingStatus> activeStatuses = activeSeatStatuses(showtime.getId());
        Set<UUID> alreadyTaken = seatIds.stream()
                .filter(activeStatuses::containsKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!alreadyTaken.isEmpty()) {
            throw new SeatUnavailableException(alreadyTaken);
        }

        // Atomic per-seat Redis acquire — the real concurrency gate. All-or-
        // nothing: any single failure releases every lock this request has
        // already acquired, and nothing is written to the database.
        List<UUID> acquiredSeatIds = new ArrayList<>();
        for (UUID seatId : seatIds) {
            if (seatLockService.tryLock(showtime.getId(), seatId, userId.toString(), HOLD_DURATION)) {
                acquiredSeatIds.add(seatId);
            } else {
                acquiredSeatIds.forEach(id -> seatLockService.unlock(showtime.getId(), id));
                throw new SeatUnavailableException(Set.of(seatId));
            }
        }

        BigDecimal totalPrice = showtime.getPrice().multiply(BigDecimal.valueOf(seats.size()));
        Instant expiresAt = Instant.now().plus(HOLD_DURATION);
        Booking booking = new Booking(user, showtime, totalPrice, expiresAt);
        // saveAndFlush: Booking has @CreationTimestamp/@UpdateTimestamp —
        // see TimestampedEntitySaveFlushRule.
        Booking savedBooking = bookingRepository.saveAndFlush(booking);

        List<BookingSeat> bookingSeats = seats.stream()
                .map(seat -> new BookingSeat(savedBooking, seat, showtime.getPrice()))
                .toList();
        bookingSeatRepository.saveAllAndFlush(bookingSeats);

        return bookingMapper.toResponse(savedBooking, bookingSeats);
    }

    @Transactional
    public void cancel(UUID userId, boolean isAdmin, UUID bookingId) {
        Booking booking = loadWithLazyExpiry(bookingId);
        requireAccess(userId, isAdmin, booking);

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Booking is " + booking.getStatus() + ", cannot be cancelled");
        }

        bookingSeatRepository.findByBookingId(bookingId)
                .forEach(bookingSeat ->
                        seatLockService.unlock(booking.getShowtime().getId(), bookingSeat.getSeat().getId()));

        booking.markCancelled();
        bookingRepository.saveAndFlush(booking);
    }

    @Transactional
    public BookingResponse getById(UUID userId, boolean isAdmin, UUID bookingId) {
        Booking booking = loadWithLazyExpiry(bookingId);
        requireAccess(userId, isAdmin, booking);
        List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(bookingId);
        return bookingMapper.toResponse(booking, bookingSeats);
    }

    private void requireAccess(UUID userId, boolean isAdmin, Booking booking) {
        if (!isAdmin && !booking.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this booking");
        }
    }

    /**
     * Lazy expiry, applied on every read of a single booking: if it's still
     * PENDING but its hold has elapsed, flip it to EXPIRED right now instead
     * of waiting on a scheduled sweep — there isn't one, see CLAUDE.md
     * Phase 5.
     */
    private Booking loadWithLazyExpiry(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        if (BookingStateMachine.shouldLazilyExpire(booking.getStatus(), booking.getExpiresAt(), Instant.now())) {
            booking.markExpired();
            bookingRepository.saveAndFlush(booking);
        }
        return booking;
    }

    /**
     * All PENDING/CONFIRMED bookings for a showtime, lazily expiring any
     * PENDING ones whose hold has elapsed first — same reasoning as
     * loadWithLazyExpiry, batched for a whole showtime instead of one
     * booking. Returns seatId -> booking status for every seat still
     * actively held (absent = AVAILABLE).
     */
    private Map<UUID, BookingStatus> activeSeatStatuses(UUID showtimeId) {
        List<Booking> candidates = bookingRepository.findByShowtimeIdAndStatusIn(showtimeId, ACTIVE_STATUSES);
        Instant now = Instant.now();

        List<Booking> newlyExpired = new ArrayList<>();
        Map<UUID, BookingStatus> statusByBookingId = new HashMap<>();
        for (Booking booking : candidates) {
            if (BookingStateMachine.shouldLazilyExpire(booking.getStatus(), booking.getExpiresAt(), now)) {
                booking.markExpired();
                newlyExpired.add(booking);
            } else {
                statusByBookingId.put(booking.getId(), booking.getStatus());
            }
        }
        if (!newlyExpired.isEmpty()) {
            bookingRepository.saveAllAndFlush(newlyExpired);
        }
        if (statusByBookingId.isEmpty()) {
            return Map.of();
        }

        Map<UUID, BookingStatus> statusBySeatId = new HashMap<>();
        bookingSeatRepository.findByBookingIdIn(statusByBookingId.keySet())
                .forEach(bookingSeat -> statusBySeatId.put(
                        bookingSeat.getSeat().getId(), statusByBookingId.get(bookingSeat.getBooking().getId())));
        return statusBySeatId;
    }

    private Showtime findShowtimeOrThrow(UUID id) {
        return showtimeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Showtime not found"));
    }

    private Integer columnSpanFor(SeatType seatType) {
        return seatType == SeatType.COUPLE ? 2 : 1;
    }
}
