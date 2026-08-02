-- Phase 5: seat booking. A booking is created PENDING the instant its
-- seats' Redis locks are acquired (see SeatLockService/BookingService) —
-- CONFIRMED is only ever set once Phase 6 wires up real payment success; no
-- code path sets it yet, but the column allows it now so this table won't
-- need another migration when that lands. EXPIRED is never swept by a
-- scheduled job — it's determined lazily, the moment anything reads a
-- PENDING booking whose expires_at has passed (see BookingStateMachine /
-- BookingService) — see CLAUDE.md Phase 5 for why.
CREATE TABLE bookings
(
    id          UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    user_id     UUID           NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    -- RESTRICT, not CASCADE — same reasoning as showtimes.movie_id/hall_id
    -- (see V8): deleting a showtime must not silently destroy booking
    -- history. ShowtimeService.delete() also checks this explicitly so the
    -- caller gets a clean 409 instead of a raw constraint violation.
    showtime_id UUID           NOT NULL REFERENCES showtimes (id) ON DELETE RESTRICT,
    status      VARCHAR(20)    NOT NULL,
    total_price NUMERIC(10, 2) NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ    NOT NULL,
    CONSTRAINT chk_bookings_status CHECK (status IN ('PENDING', 'CONFIRMED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT chk_bookings_total_price CHECK (total_price >= 0)
);

CREATE INDEX idx_bookings_user_id ON bookings (user_id);
CREATE INDEX idx_bookings_showtime_id ON bookings (showtime_id);
CREATE INDEX idx_bookings_showtime_status ON bookings (showtime_id, status);

-- booking_seats is a pure ownership/junction relationship — unlike a
-- showtime (an independently meaningful record on its own), a booking_seats
-- row has no meaning without its parent booking, so CASCADE here is
-- correct and intentional, not the V7 mistake repeated.
CREATE TABLE booking_seats
(
    id               UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    booking_id       UUID          NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    seat_id          UUID          NOT NULL REFERENCES seats (id) ON DELETE RESTRICT,
    -- Snapshot of the showtime price at booking time — later price changes
    -- on the showtime must not retroactively alter historical orders.
    price_at_booking NUMERIC(8, 2) NOT NULL,
    CONSTRAINT chk_booking_seats_price CHECK (price_at_booking >= 0),
    CONSTRAINT uq_booking_seats_booking_seat UNIQUE (booking_id, seat_id)
);

CREATE INDEX idx_booking_seats_booking_id ON booking_seats (booking_id);
CREATE INDEX idx_booking_seats_seat_id ON booking_seats (seat_id);
