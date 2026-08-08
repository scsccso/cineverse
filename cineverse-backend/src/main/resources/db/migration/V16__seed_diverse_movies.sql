-- Seed: 10 real, genre-diverse movies (2026-08-08 seed data expansion,
-- follow-up to V14's single Dune Part Three poster fix) — replaces the
-- test fixtures V15 just removed, so the home page's carousel and
-- now-playing grid have enough visually distinct posters to actually
-- demonstrate the layout instead of mostly showing the "暂无海报"
-- placeholder. Same OMDb-matching process and confidence rule as V14 (see
-- CLAUDE.md "种子数据的海报图来源"): every title below was queried against
-- OMDb's t= exact-title endpoint and returned exactly one unambiguous
-- result, no candidate list to pick from — all high-confidence, nothing
-- held back for manual review this round.
--
-- poster_url/backdrop_url reuse the same OMDb/Amazon-hosted image (same
-- reasoning as V14 — no separate backdrop art on OMDb's free tier).
-- Taglines are original one-liners written for this seed data, not lifted
-- from real marketing copy — OMDb doesn't provide a tagline field, and
-- reproducing studio ad copy verbatim isn't necessary just to fill this
-- column. Everything else (description/content_rating/user_rating/
-- duration_minutes) comes directly from OMDb's Plot/Rated/imdbRating/
-- Runtime fields for the matched title.
--
-- Genres are mapped onto this project's fixed 15-value genre list (see
-- V4__seed_genres.sql — there's no genre-management API, this is the only
-- source). OMDb's own genre tags don't always fit: Oppenheimer's OMDb
-- genres are "Biography, Drama, History", but this project has neither
-- Biography nor History as a genre — mapped to Drama + War instead (the
-- film is fundamentally about the WWII-era Manhattan Project), the closest
-- fit from what's actually available, not a literal translation of OMDb's
-- tags.
--
-- Explicit UUID literals (not gen_random_uuid()), same convention as
-- V6__seed_cinema_halls_seats.sql, so this migration can link movie_genres
-- rows to a specific movie within the same script.

INSERT INTO movies (id, title, description, tagline, duration_minutes, content_rating, user_rating, poster_url, backdrop_url, status)
VALUES
    -- Interstellar (2014) — OMDb imdbID tt0816692
    ('30000000-0000-0000-0000-000000000001', 'Interstellar',
     'When Earth becomes uninhabitable in the future, a farmer and ex-NASA pilot, Joseph Cooper, is tasked to pilot a spacecraft, along with a team of researchers, to find a new planet for humans.',
     'Somewhere beyond this world, hope is waiting.',
     169, 'PG-13', 8.7,
     'https://m.media-amazon.com/images/M/MV5BYzdjMDAxZGItMjI2My00ODA1LTlkNzItOWFjMDU5ZDJlYWY3XkEyXkFqcGc@._V1_QL75_UX380_CR0,0,380,562_.jpg',
     'https://m.media-amazon.com/images/M/MV5BYzdjMDAxZGItMjI2My00ODA1LTlkNzItOWFjMDU5ZDJlYWY3XkEyXkFqcGc@._V1_QL75_UX380_CR0,0,380,562_.jpg',
     'NOW_PLAYING'),

    -- The Shawshank Redemption (1994) — OMDb imdbID tt0111161
    ('30000000-0000-0000-0000-000000000002', 'The Shawshank Redemption',
     'A wrongfully convicted banker forms a close friendship with a hardened convict over a quarter century while retaining his humanity through simple acts of compassion.',
     'Some birds aren''t meant to be caged.',
     142, 'R', 9.3,
     'https://m.media-amazon.com/images/M/MV5BMDAyY2FhYjctNDc5OS00MDNlLThiMGUtY2UxYWVkNGY2ZjljXkEyXkFqcGc@._V1_QL75_UX380_CR0,4,380,562_.jpg',
     'https://m.media-amazon.com/images/M/MV5BMDAyY2FhYjctNDc5OS00MDNlLThiMGUtY2UxYWVkNGY2ZjljXkEyXkFqcGc@._V1_QL75_UX380_CR0,4,380,562_.jpg',
     'NOW_PLAYING'),

    -- Mad Max: Fury Road (2015) — OMDb imdbID tt1392190
    ('30000000-0000-0000-0000-000000000003', 'Mad Max: Fury Road',
     'In a post-apocalyptic wasteland, a woman rebels against a tyrannical ruler in search for her homeland with the aid of a group of female prisoners, a psychotic worshipper and a drifter named Max.',
     'In the wasteland, hope has a name.',
     120, 'R', 8.1,
     'https://m.media-amazon.com/images/M/MV5BZDRkODJhOTgtOTc1OC00NTgzLTk4NjItNDgxZDY4YjlmNDY2XkEyXkFqcGc@._V1_SX300.jpg',
     'https://m.media-amazon.com/images/M/MV5BZDRkODJhOTgtOTc1OC00NTgzLTk4NjItNDgxZDY4YjlmNDY2XkEyXkFqcGc@._V1_SX300.jpg',
     'NOW_PLAYING'),

    -- Spider-Man: Into the Spider-Verse (2018) — OMDb imdbID tt4633694
    ('30000000-0000-0000-0000-000000000004', 'Spider-Man: Into the Spider-Verse',
     'Teen Miles Morales becomes the Spider-Man of his universe and must join with five spider-powered individuals from other dimensions to stop a threat for all realities.',
     'Everyone can wear the mask.',
     117, 'PG', 8.4,
     'https://m.media-amazon.com/images/M/MV5BMjMwNDkxMTgzOF5BMl5BanBnXkFtZTgwNTkwNTQ3NjM@._V1_SX300.jpg',
     'https://m.media-amazon.com/images/M/MV5BMjMwNDkxMTgzOF5BMl5BanBnXkFtZTgwNTkwNTQ3NjM@._V1_SX300.jpg',
     'NOW_PLAYING'),

    -- The Grand Budapest Hotel (2014) — OMDb imdbID tt2278388
    ('30000000-0000-0000-0000-000000000005', 'The Grand Budapest Hotel',
     'A writer encounters the owner of an aging high-class hotel, who tells him of his early years serving as a lobby boy in the hotel''s glorious years under an exceptional concierge.',
     'Elegance, chaos, and a hotel worth fighting for.',
     99, 'R', 8.1,
     'https://m.media-amazon.com/images/M/MV5BMzM5NjUxOTEyMl5BMl5BanBnXkFtZTgwNjEyMDM0MDE@._V1_QL75_UX380_CR0,0,380,562_.jpg',
     'https://m.media-amazon.com/images/M/MV5BMzM5NjUxOTEyMl5BMl5BanBnXkFtZTgwNjEyMDM0MDE@._V1_QL75_UX380_CR0,0,380,562_.jpg',
     'NOW_PLAYING'),

    -- Get Out (2017) — OMDb imdbID tt5052448
    ('30000000-0000-0000-0000-000000000006', 'Get Out',
     'A young African-American visits his white girlfriend''s parents for the weekend, where his simmering uneasiness about their reception of him eventually reaches a boiling point.',
     'The truth will terrify you.',
     104, 'R', 7.8,
     'https://m.media-amazon.com/images/M/MV5BMjUxMDQwNjcyNl5BMl5BanBnXkFtZTgwNzcwMzc0MTI@._V1_QL75_UX380_CR0,0,380,562_.jpg',
     'https://m.media-amazon.com/images/M/MV5BMjUxMDQwNjcyNl5BMl5BanBnXkFtZTgwNzcwMzc0MTI@._V1_QL75_UX380_CR0,0,380,562_.jpg',
     'NOW_PLAYING'),

    -- Pulp Fiction (1994) — OMDb imdbID tt0110912
    ('30000000-0000-0000-0000-000000000007', 'Pulp Fiction',
     'The lives of two mob hitmen, a boxer, a gangster and his wife, and a pair of diner bandits intertwine in four tales of violence and redemption.',
     'Everybody has a story worth telling twice.',
     154, 'R', 8.8,
     'https://m.media-amazon.com/images/M/MV5BYTViYTE3ZGQtNDBlMC00ZTAyLTkyODMtZGRiZDg0MjA2YThkXkEyXkFqcGc@._V1_QL75_UY562_CR3,0,380,562_.jpg',
     'https://m.media-amazon.com/images/M/MV5BYTViYTE3ZGQtNDBlMC00ZTAyLTkyODMtZGRiZDg0MjA2YThkXkEyXkFqcGc@._V1_QL75_UY562_CR3,0,380,562_.jpg',
     'NOW_PLAYING'),

    -- The Lord of the Rings: The Fellowship of the Ring (2001) — OMDb imdbID tt0120737
    ('30000000-0000-0000-0000-000000000008', 'The Lord of the Rings: The Fellowship of the Ring',
     'A meek Hobbit from the Shire and eight companions set out on a journey to destroy the powerful One Ring and save Middle-earth from the Dark Lord Sauron.',
     'One journey. Nine companions. A world at stake.',
     178, 'PG-13', 8.9,
     'https://m.media-amazon.com/images/M/MV5BNzIxMDQ2YTctNDY4MC00ZTRhLTk4ODQtMTVlOWY4NTdiYmMwXkEyXkFqcGc@._V1_QL75_UX380_CR0,1,380,562_.jpg',
     'https://m.media-amazon.com/images/M/MV5BNzIxMDQ2YTctNDY4MC00ZTRhLTk4ODQtMTVlOWY4NTdiYmMwXkEyXkFqcGc@._V1_QL75_UX380_CR0,1,380,562_.jpg',
     'NOW_PLAYING'),

    -- Oppenheimer (2023) — OMDb imdbID tt15398776
    ('30000000-0000-0000-0000-000000000009', 'Oppenheimer',
     'A dramatization of the life story of J. Robert Oppenheimer, the physicist who had a large hand in the development of the atomic bombs that brought an end to World War II.',
     'The man who built the bomb that changed everything.',
     180, 'R', 8.2,
     'https://m.media-amazon.com/images/M/MV5BN2JkMDc5MGQtZjg3YS00NmFiLWIyZmQtZTJmNTM5MjVmYTQ4XkEyXkFqcGc@._V1_QL75_UX380_CR0,0,380,562_.jpg',
     'https://m.media-amazon.com/images/M/MV5BN2JkMDc5MGQtZjg3YS00NmFiLWIyZmQtZTJmNTM5MjVmYTQ4XkEyXkFqcGc@._V1_QL75_UX380_CR0,0,380,562_.jpg',
     'NOW_PLAYING'),

    -- Spirited Away (2001 JP release; OMDb records the 2003 US release year) — OMDb imdbID tt0245429
    ('30000000-0000-0000-0000-000000000010', 'Spirited Away',
     'During her family''s move to the suburbs, a sullen 10-year-old girl wanders into a world ruled by gods, witches and spirits, and where humans are changed into beasts.',
     'Some doors, once opened, change you forever.',
     124, 'PG', 8.6,
     'https://m.media-amazon.com/images/M/MV5BNTEyNmEwOWUtYzkyOC00ZTQ4LTllZmUtMjk0Y2YwOGUzYjRiXkEyXkFqcGc@._V1_QL75_UX380_CR0,0,380,562_.jpg',
     'https://m.media-amazon.com/images/M/MV5BNTEyNmEwOWUtYzkyOC00ZTQ4LTllZmUtMjk0Y2YwOGUzYjRiXkEyXkFqcGc@._V1_QL75_UX380_CR0,0,380,562_.jpg',
     'NOW_PLAYING');

INSERT INTO movie_genres (movie_id, genre_id)
SELECT movie_id, genres.id
FROM (VALUES
    ('30000000-0000-0000-0000-000000000001'::uuid, 'Adventure'), ('30000000-0000-0000-0000-000000000001'::uuid, 'Drama'), ('30000000-0000-0000-0000-000000000001'::uuid, 'Sci-Fi'),
    ('30000000-0000-0000-0000-000000000002'::uuid, 'Drama'),
    ('30000000-0000-0000-0000-000000000003'::uuid, 'Action'), ('30000000-0000-0000-0000-000000000003'::uuid, 'Adventure'), ('30000000-0000-0000-0000-000000000003'::uuid, 'Sci-Fi'),
    ('30000000-0000-0000-0000-000000000004'::uuid, 'Animation'), ('30000000-0000-0000-0000-000000000004'::uuid, 'Action'), ('30000000-0000-0000-0000-000000000004'::uuid, 'Adventure'),
    ('30000000-0000-0000-0000-000000000005'::uuid, 'Comedy'), ('30000000-0000-0000-0000-000000000005'::uuid, 'Drama'),
    ('30000000-0000-0000-0000-000000000006'::uuid, 'Horror'), ('30000000-0000-0000-0000-000000000006'::uuid, 'Mystery'), ('30000000-0000-0000-0000-000000000006'::uuid, 'Thriller'),
    ('30000000-0000-0000-0000-000000000007'::uuid, 'Crime'), ('30000000-0000-0000-0000-000000000007'::uuid, 'Drama'),
    ('30000000-0000-0000-0000-000000000008'::uuid, 'Adventure'), ('30000000-0000-0000-0000-000000000008'::uuid, 'Drama'), ('30000000-0000-0000-0000-000000000008'::uuid, 'Fantasy'),
    ('30000000-0000-0000-0000-000000000009'::uuid, 'Drama'), ('30000000-0000-0000-0000-000000000009'::uuid, 'War'),
    ('30000000-0000-0000-0000-000000000010'::uuid, 'Animation'), ('30000000-0000-0000-0000-000000000010'::uuid, 'Adventure'), ('30000000-0000-0000-0000-000000000010'::uuid, 'Family')
) AS mapping(movie_id, genre_name)
         JOIN genres ON genres.name = mapping.genre_name;
