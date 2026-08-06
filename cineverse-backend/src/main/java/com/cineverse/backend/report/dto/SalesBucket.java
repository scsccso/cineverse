package com.cineverse.backend.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One time-bucket row of the sales report. {@code periodStart} is the local
 * (cinema-timezone) calendar date the bucket starts on — always present for
 * every bucket in the requested range even when revenue is zero (the
 * repository fills gaps via {@code generate_series}, so the frontend chart
 * never has to invent a zero-value point itself).
 */
public record SalesBucket(LocalDate periodStart, BigDecimal revenue, long bookingCount) {
}
