package com.cineverse.backend.cinema.repository;

import java.util.UUID;

/** Total bookable seat units (one row per COUPLE seat, not two) for a hall — see {@link SeatRepository#countByHallIdIn}. */
public record HallSeatCount(UUID hallId, long totalSeats) {
}
