package com.cineverse.backend.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/admin/reports/sales. {@code totalRevenue} only counts
 * CONFIRMED bookings' SUCCEEDED payments; {@code pendingReconciliationAmount}
 * is the separate ORPHANED_SUCCESS total for the same range/filters — money
 * Stripe actually captured but not (yet) applied to a booking, surfaced
 * rather than silently dropped. See CLAUDE.md Phase 8 for the full revenue
 * accounting rule.
 */
public record SalesReportResponse(
        LocalDate from,
        LocalDate to,
        ReportGranularity granularity,
        UUID movieId,
        UUID hallId,
        String currency,
        List<SalesBucket> buckets,
        BigDecimal totalRevenue,
        BigDecimal pendingReconciliationAmount) {
}
