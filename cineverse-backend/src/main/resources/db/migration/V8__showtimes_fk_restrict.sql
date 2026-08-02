-- V7 declared movie_id/hall_id as ON DELETE CASCADE without thinking it
-- through: deleting a Movie or Hall would have silently wiped out every
-- Showtime that referenced it. For a booking system that's the wrong
-- default — an admin mis-deleting a movie should not be able to destroy
-- scheduling data as a side effect. Explicit business decision: RESTRICT.
-- Movie deletion is blocked at the service layer first (MovieService.delete
-- checks ShowtimeRepository.existsByMovieId and returns a clean 409); this
-- constraint is the defense-in-depth backstop for any path that bypasses
-- the service (raw SQL, a future admin tool, etc).
--
-- Hall has no delete endpoint yet (Phase 3 MVP boundary), so this can't be
-- triggered today, but the FK is fixed now anyway so it fails safe from day
-- one. Whenever a Hall delete endpoint is added, HallService must add the
-- same kind of existsByHallId guard MovieService already has for movies
-- (see ShowtimeRepository) instead of relying only on this constraint to
-- surface a raw DB error.

ALTER TABLE showtimes DROP CONSTRAINT showtimes_movie_id_fkey;
ALTER TABLE showtimes
    ADD CONSTRAINT showtimes_movie_id_fkey
        FOREIGN KEY (movie_id) REFERENCES movies (id) ON DELETE RESTRICT;

ALTER TABLE showtimes DROP CONSTRAINT showtimes_hall_id_fkey;
ALTER TABLE showtimes
    ADD CONSTRAINT showtimes_hall_id_fkey
        FOREIGN KEY (hall_id) REFERENCES halls (id) ON DELETE RESTRICT;
