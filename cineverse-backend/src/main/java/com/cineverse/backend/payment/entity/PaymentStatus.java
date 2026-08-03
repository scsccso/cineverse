package com.cineverse.backend.payment.entity;

public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    /**
     * Stripe reports the payment succeeded, but the booking it belongs to
     * was no longer PENDING by the time the webhook was processed (already
     * released — expired or cancelled — possibly with the seat since
     * re-booked by someone else). Money was captured; it is deliberately
     * NOT auto-applied to the booking and NOT auto-refunded — flagged here
     * for manual reconciliation. See CLAUDE.md Phase 6.
     */
    ORPHANED_SUCCESS
}
