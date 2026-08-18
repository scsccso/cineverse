-- Movie status change history (post-Phase-8 admin feature, see CLAUDE.md
-- "电影状态变更历史"): every time a movie's status actually changes, one row
-- is written here — via PATCH /api/v1/movies/{id}/status, the only path
-- allowed to change status (MovieService.update() rejects a status
-- difference arriving through PUT with a 409, see MovieStatusChangeNotAllowedException).
-- The very first row a movie ever gets is written by MovieService.create()
-- itself (from_status = NULL) — that's the initial state, not a "change" in
-- the transition sense, but it still deserves a row so "when did this movie
-- enter its first status" is always answerable without a separate fallback
-- to movies.created_at.
--
-- movie_id is ON DELETE CASCADE, not the RESTRICT this project otherwise
-- defaults to around movies (see V8: showtimes.movie_id/hall_id are RESTRICT
-- specifically because deleting a movie must never silently destroy
-- transactional records like bookings). This table is pure audit metadata
-- with no transactional consequence of its own — once a movie is deletable
-- at all (only possible when it already has zero scheduled showtimes, per
-- MovieService.delete()), its status history has no independent meaning and
-- nothing else references it, so cascading it away is the deliberate
-- choice here, not an oversight repeating the V7 "written without thinking
-- about it" mistake.
--
-- changed_by is ON DELETE SET NULL, not CASCADE/RESTRICT: if the admin
-- account that made a change is later deleted (admin user management
-- supports deleting users), the fact that the change happened must survive
-- — only the "who" is lost, displayed as "—" by the frontend.
CREATE TABLE movie_status_history
(
    id          UUID PRIMARY KEY    DEFAULT gen_random_uuid(),
    movie_id    UUID        NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    changed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_movie_status_history_from_status CHECK (from_status IN ('NOW_PLAYING', 'COMING_SOON', 'ENDED')),
    CONSTRAINT chk_movie_status_history_to_status CHECK (to_status IN ('NOW_PLAYING', 'COMING_SOON', 'ENDED'))
);

-- Serves the one query this feature runs: "give me this movie's full
-- timeline, newest first" (GET /api/v1/admin/movies/{id}/status-history).
CREATE INDEX idx_movie_status_history_movie_id_changed_at ON movie_status_history (movie_id, changed_at DESC);
