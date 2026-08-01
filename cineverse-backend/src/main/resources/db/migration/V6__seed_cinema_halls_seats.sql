-- Seed: 1 cinema, 3 halls, seats auto-generated per hall.
-- Policy (mirrored in SeatLayoutGenerator for admin-created halls): the
-- last row of every hall is COUPLE seats, paired every 2 columns; every
-- other row is STANDARD, one seat per column. All three halls use an even
-- total_columns so every couple row pairs up evenly with none left over.

INSERT INTO cinemas (id, name, address)
VALUES ('11111111-1111-1111-1111-111111111111', 'CineVerse Downtown', '1 Cinema Plaza, Kuala Lumpur');

INSERT INTO halls (id, cinema_id, name, total_rows, total_columns)
VALUES ('21111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Hall 1', 6, 10),
       ('22222222-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Hall 2', 8, 12),
       ('23333333-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Hall 3', 10, 14);

-- Hall 1: 6 rows x 10 columns. Rows A-E standard, row F (last) couple.
INSERT INTO seats (id, hall_id, row_label, column_number, seat_type)
SELECT gen_random_uuid(), '21111111-1111-1111-1111-111111111111', chr(64 + r), c, 'STANDARD'
FROM generate_series(1, 5) AS r
         CROSS JOIN generate_series(1, 10) AS c;

INSERT INTO seats (id, hall_id, row_label, column_number, seat_type)
SELECT gen_random_uuid(), '21111111-1111-1111-1111-111111111111', chr(64 + 6), c, 'COUPLE'
FROM generate_series(1, 10, 2) AS c;

-- Hall 2: 8 rows x 12 columns. Rows A-G standard, row H (last) couple.
INSERT INTO seats (id, hall_id, row_label, column_number, seat_type)
SELECT gen_random_uuid(), '22222222-1111-1111-1111-111111111111', chr(64 + r), c, 'STANDARD'
FROM generate_series(1, 7) AS r
         CROSS JOIN generate_series(1, 12) AS c;

INSERT INTO seats (id, hall_id, row_label, column_number, seat_type)
SELECT gen_random_uuid(), '22222222-1111-1111-1111-111111111111', chr(64 + 8), c, 'COUPLE'
FROM generate_series(1, 12, 2) AS c;

-- Hall 3: 10 rows x 14 columns. Rows A-I standard, row J (last) couple.
INSERT INTO seats (id, hall_id, row_label, column_number, seat_type)
SELECT gen_random_uuid(), '23333333-1111-1111-1111-111111111111', chr(64 + r), c, 'STANDARD'
FROM generate_series(1, 9) AS r
         CROSS JOIN generate_series(1, 14) AS c;

INSERT INTO seats (id, hall_id, row_label, column_number, seat_type)
SELECT gen_random_uuid(), '23333333-1111-1111-1111-111111111111', chr(64 + 10), c, 'COUPLE'
FROM generate_series(1, 14, 2) AS c;
