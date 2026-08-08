-- Data patch, not a schema change: backfills backdrop_url from TMDB (not
-- OMDb) for all 11 movies currently in the table. Only backdrop_url is
-- touched here — poster_url/title/description/content_rating/user_rating
-- stay exactly as V14/V16 set them from OMDb, already verified there.
--
-- Why a second data source instead of reusing OMDb again: OMDb's free tier
-- has no dedicated backdrop/banner field, which is why V14/V16 pointed
-- backdrop_url at the same poster image as a stopgap — visibly wrong for a
-- horizontal hero banner (a portrait poster stretched to fill a landscape
-- area, at a resolution meant for a poster thumbnail, not a full-bleed
-- background). TMDB's `backdrop_path` field exists specifically for this
-- use case (real landscape stills, up to 1280px wide via the `/t/p/w1280/`
-- prefix) — using the right source for the right field, not "make OMDb do
-- something it wasn't built for." See CLAUDE.md for the full writeup.
--
-- Matched via TMDB's /search/movie endpoint (title + year query param,
-- same disambiguation approach as OMDb's t=/y=): every title below was an
-- unambiguous top result — matching title, matching release year, and (for
-- the few queries that returned more than one candidate) far higher
-- popularity than any other candidate — so all 11 are high-confidence,
-- nothing held back for manual review this round.
--
-- WHERE matches by title, not id, same reasoning as V14/V15/V16.

UPDATE movies SET backdrop_url = 'https://image.tmdb.org/t/p/w1280/i5E9H7Ik0u61ylDDTbmUpTL3Yw.jpg'
WHERE title = 'Dune Part Three';

UPDATE movies SET backdrop_url = 'https://image.tmdb.org/t/p/w1280/5XNQBqnBwPA9yT0jZ0p3s8bbLh0.jpg'
WHERE title = 'Interstellar';

UPDATE movies SET backdrop_url = 'https://image.tmdb.org/t/p/w1280/zfbjgQE1uSd9wiPTX4VzsLi0rGG.jpg'
WHERE title = 'The Shawshank Redemption';

UPDATE movies SET backdrop_url = 'https://image.tmdb.org/t/p/w1280/uT895WNwm0aIJRtGizcQhrejWUo.jpg'
WHERE title = 'Mad Max: Fury Road';

UPDATE movies SET backdrop_url = 'https://image.tmdb.org/t/p/w1280/8mnXR9rey5uQ08rZAvzojKWbDQS.jpg'
WHERE title = 'Spider-Man: Into the Spider-Verse';

UPDATE movies SET backdrop_url = 'https://image.tmdb.org/t/p/w1280/9udCLTxTFl28RxnK8Q05E154ZGa.jpg'
WHERE title = 'The Grand Budapest Hotel';

UPDATE movies SET backdrop_url = 'https://image.tmdb.org/t/p/w1280/o8dPH0ZSIyyViP6rjRX1djwCUwI.jpg'
WHERE title = 'Get Out';

UPDATE movies SET backdrop_url = 'https://image.tmdb.org/t/p/w1280/suaEOtk1N1sgg2MTM7oZd2cfVp3.jpg'
WHERE title = 'Pulp Fiction';

UPDATE movies SET backdrop_url = 'https://image.tmdb.org/t/p/w1280/mWDdRXTivGE7aaY2vo1Ie0PfCX5.jpg'
WHERE title = 'The Lord of the Rings: The Fellowship of the Ring';

UPDATE movies SET backdrop_url = 'https://image.tmdb.org/t/p/w1280/neeNHeXjMF5fXoCJRsOmkNGC7q.jpg'
WHERE title = 'Oppenheimer';

UPDATE movies SET backdrop_url = 'https://image.tmdb.org/t/p/w1280/dyJvKsNs2KP8qQnAXbRwDjblViy.jpg'
WHERE title = 'Spirited Away';
