-- Data patch, not a schema change: backfills poster_url (and backdrop_url,
-- reusing the same image — OMDb's free tier doesn't return a separate
-- backdrop/banner image) for movies whose title matches a real film in the
-- OMDb database. Unlike the other seed data in this migration path, these
-- movie rows were originally created ad hoc through the running app (Admin
-- API / manual testing), not by an earlier Flyway seed script — this is
-- still the right mechanism to persist the fix anyway: it makes the poster
-- assignment reproducible if the Postgres volume is ever wiped and
-- recreated, instead of a one-off manual UPDATE that only affects the
-- current dev database and would otherwise be silently lost.
--
-- Matched via OMDb (https://www.omdbapi.com/), title+year query
-- "Dune Part Three" -> exact single-result hit "Dune: Part Three" (2026,
-- dir. Denis Villeneuve, imdbID tt31378509) — see CLAUDE.md for the
-- confidence-matching writeup and attribution requirement (OMDb's terms
-- require crediting the API as the data/image source, see README.md).
--
-- WHERE matches by title, not id — the id in this dev database is
-- environment-specific (generated at insert time) and wouldn't exist in a
-- freshly created database; matching by title is a no-op (0 rows affected,
-- not an error) anywhere this exact title doesn't exist, which is the
-- correct behavior for a data patch like this.
UPDATE movies
SET poster_url   = 'https://m.media-amazon.com/images/M/MV5BYjk1NjgwZDMtYzI5OS00Y2Q4LWI1NmItNTE5OGU2NDVmMTAxXkEyXkFqcGc@._V1_QL75_UY562_CR35,0,380,562_.jpg',
    backdrop_url = 'https://m.media-amazon.com/images/M/MV5BYjk1NjgwZDMtYzI5OS00Y2Q4LWI1NmItNTE5OGU2NDVmMTAxXkEyXkFqcGc@._V1_QL75_UY562_CR35,0,380,562_.jpg'
WHERE title = 'Dune Part Three';
