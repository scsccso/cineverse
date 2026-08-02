-- Showtime Scheduling (Phase 4): movie + hall + time slot.
-- end_time = start_time + movie.duration_minutes ONLY (no buffer baked in);
-- the 20-minute cleanup buffer is enforced separately, at conflict-check
-- time, against [start_time, end_time + 20min] — see ShowtimeOverlapChecker.
-- No update path is offered at the application layer (delete + recreate
-- instead), so there is deliberately no trigger keeping end_time in sync
-- with a changed start_time/movie.

CREATE TABLE showtimes
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    movie_id   UUID          NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    hall_id    UUID          NOT NULL REFERENCES halls (id) ON DELETE CASCADE,
    start_time TIMESTAMPTZ   NOT NULL,
    end_time   TIMESTAMPTZ   NOT NULL,
    -- Flat base price for now — no per-seat-type pricing (STANDARD vs
    -- COUPLE) until Phase 6.
    price      NUMERIC(8, 2) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT chk_showtimes_time_order CHECK (end_time > start_time),
    CONSTRAINT chk_showtimes_price CHECK (price >= 0)
);

CREATE INDEX idx_showtimes_hall_id ON showtimes (hall_id);
CREATE INDEX idx_showtimes_movie_id ON showtimes (movie_id);
CREATE INDEX idx_showtimes_start_time ON showtimes (start_time);
