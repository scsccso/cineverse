-- No genre-management API in this phase (out of scope) — seed a fixed set
-- of common genres so movie_genres has something to reference.
INSERT INTO genres (name)
VALUES ('Action'),
       ('Adventure'),
       ('Animation'),
       ('Comedy'),
       ('Crime'),
       ('Documentary'),
       ('Drama'),
       ('Family'),
       ('Fantasy'),
       ('Horror'),
       ('Mystery'),
       ('Romance'),
       ('Sci-Fi'),
       ('Thriller'),
       ('War');
