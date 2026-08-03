-- Adds ORPHANED_SUCCESS: a payment Stripe reports as successful, but whose
-- booking was no longer PENDING by the time the webhook was processed (see
-- PaymentService.handleCheckoutSessionCompleted / CLAUDE.md Phase 6). Money
-- was captured; this status exists so that fact is discoverable for manual
-- reconciliation instead of silently lost, without auto-applying it to a
-- booking that may no longer own the seat.
ALTER TABLE payments
    DROP CONSTRAINT chk_payments_status;

ALTER TABLE payments
    ADD CONSTRAINT chk_payments_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'ORPHANED_SUCCESS'));
