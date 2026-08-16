package com.cineverse.backend.payment.dto;

import com.cineverse.backend.booking.entity.BookingStatus;
import com.cineverse.backend.payment.entity.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of GET /api/v1/admin/payments — the manual-reconciliation
 * drill-down Phase 8's reports never provided (they only ever summed
 * ORPHANED_SUCCESS amounts into one pendingReconciliationAmount figure, see
 * SalesReportResponse). stripeSessionId/stripePaymentIntentId are included
 * specifically because reconciling one of these means looking the payment
 * up in the Stripe dashboard next.
 */
public record AdminPaymentResponse(
        UUID id,
        PaymentStatus status,
        @Schema(example = "50.00") BigDecimal amount,
        @Schema(example = "myr") String currency,
        String stripeSessionId,
        String stripePaymentIntentId,
        Instant createdAt,
        @Schema(description = "For a terminal status, the moment it became that — see Payment.markSucceeded/"
                + "markFailed/markOrphanedSuccess, all called exactly once per row's lifecycle")
        Instant updatedAt,
        BookingSummary booking) {

    public record BookingSummary(
            UUID id,
            BookingStatus status,
            @Schema(example = "moviefan@example.com") String userEmail,
            @Schema(example = "Interstellar") String movieTitle,
            @Schema(example = "Hall 1") String hallName,
            Instant showtimeStartTime) {
    }
}
