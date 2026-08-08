-- Data cleanup, not a schema change: removes the three obvious test
-- fixtures identified during the 2026-08-08 OMDb poster audit ("Verify
-- Fix", "Verify Movie", "E2E Test Movie" — created ad hoc through manual
-- testing / E2E runs, not real seed data; see CLAUDE.md "种子数据的海报图
--来源"). Matches by title, not id, same reasoning as V14: the ids in this
-- database are runtime-generated and won't exist in a freshly created one,
-- so title-matching keeps this a safe no-op anywhere this exact data
-- doesn't exist.
--
-- Deletion order follows the FK chain exactly (all three of these
-- relationships are ON DELETE RESTRICT, not CASCADE — see V8/V9/V10 and
-- CLAUDE.md's "ON DELETE RESTRICT 一览" in docs/DATABASE.md):
--   payments (RESTRICT on booking_id) -> bookings (RESTRICT on
--   showtime_id; booking_seats cascades automatically) -> showtimes
--   (RESTRICT on movie_id) -> movies (movie_genres cascades automatically).
-- Two of these three test movies had real showtimes/bookings/payments
-- attached from earlier manual testing (Stripe checkout flows, seat
-- locking, etc.) — deleting movies directly would fail against the
-- RESTRICT constraint without first clearing this chain.

DELETE FROM payments
WHERE booking_id IN (
    SELECT b.id
    FROM bookings b
             JOIN showtimes s ON s.id = b.showtime_id
             JOIN movies m ON m.id = s.movie_id
    WHERE m.title IN ('Verify Fix', 'Verify Movie', 'E2E Test Movie')
);

DELETE FROM bookings
WHERE showtime_id IN (
    SELECT s.id
    FROM showtimes s
             JOIN movies m ON m.id = s.movie_id
    WHERE m.title IN ('Verify Fix', 'Verify Movie', 'E2E Test Movie')
);

DELETE FROM showtimes
WHERE movie_id IN (
    SELECT id FROM movies WHERE title IN ('Verify Fix', 'Verify Movie', 'E2E Test Movie')
);

DELETE FROM movies
WHERE title IN ('Verify Fix', 'Verify Movie', 'E2E Test Movie');
