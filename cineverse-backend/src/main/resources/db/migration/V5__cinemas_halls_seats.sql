-- Cinema & Hall Management (Phase 3): cinemas -> halls -> seats.
-- MVP is 1 cinema / 3 halls, but the schema doesn't assume that — cinemas
-- and halls are proper FK-linked tables, not hardcoded.

CREATE TABLE cinemas
(
    id         UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    address    VARCHAR(500),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE halls
(
    id            UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    cinema_id     UUID         NOT NULL REFERENCES cinemas (id) ON DELETE CASCADE,
    name          VARCHAR(255) NOT NULL,
    total_rows    INTEGER      NOT NULL,
    total_columns INTEGER      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_halls_dimensions CHECK (total_rows > 0 AND total_columns > 0)
);

-- No updated_at: seats are immutable once generated. Changing a layout
-- means deleting and recreating the hall, not patching individual seats.
CREATE TABLE seats
(
    id            UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    hall_id       UUID        NOT NULL REFERENCES halls (id) ON DELETE CASCADE,
    row_label     VARCHAR(5)  NOT NULL,
    column_number INTEGER     NOT NULL,
    seat_type     VARCHAR(20) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_seats_type CHECK (seat_type IN ('STANDARD', 'COUPLE')),
    CONSTRAINT chk_seats_column CHECK (column_number > 0),
    -- Guards against duplicate starting coordinates. It can't fully catch a
    -- COUPLE seat's second (implied) column overlapping a neighbor — that's
    -- guaranteed by construction in SeatLayoutGenerator instead, see its
    -- unit test.
    CONSTRAINT uq_seats_hall_row_column UNIQUE (hall_id, row_label, column_number)
);

CREATE INDEX idx_halls_cinema_id ON halls (cinema_id);
CREATE INDEX idx_seats_hall_id ON seats (hall_id);
