-- Movie Management (Phase 2): movies + genres (+ join table).
-- status is VARCHAR + CHECK, same convention as users.role — see V1.

CREATE TABLE genres
(
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_genres_name UNIQUE (name)
);

CREATE TABLE movies
(
    id                UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    title             VARCHAR(255) NOT NULL,
    description       TEXT,
    tagline           VARCHAR(500),
    duration_minutes  INTEGER      NOT NULL,
    -- MPAA-style classification, e.g. "PG-13" — deliberately a separate
    -- concept from user_rating below, do not conflate the two.
    content_rating    VARCHAR(20),
    -- Admin-entered aggregate score for now; the Reviews module (Backlog)
    -- may replace this with an auto-computed average from user reviews.
    user_rating       NUMERIC(3, 1),
    poster_url        VARCHAR(500),
    backdrop_url      VARCHAR(500),
    trailer_url       VARCHAR(500),
    status            VARCHAR(20)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_movies_status CHECK (status IN ('NOW_PLAYING', 'COMING_SOON', 'ENDED')),
    CONSTRAINT chk_movies_user_rating CHECK (user_rating IS NULL OR (user_rating >= 0 AND user_rating <= 10)),
    CONSTRAINT chk_movies_duration CHECK (duration_minutes > 0)
);

CREATE TABLE movie_genres
(
    movie_id UUID NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    genre_id UUID NOT NULL REFERENCES genres (id) ON DELETE CASCADE,
    PRIMARY KEY (movie_id, genre_id)
);

CREATE INDEX idx_movies_status ON movies (status);
CREATE INDEX idx_movie_genres_genre_id ON movie_genres (genre_id);
