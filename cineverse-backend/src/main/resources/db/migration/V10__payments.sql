-- Phase 6: Stripe Checkout payments. One booking can end up with more than
-- one row here (an abandoned/expired checkout attempt followed by a
-- successful retry both leave a row — see PaymentService), so booking_id is
-- indexed but NOT unique. stripe_session_id IS unique: it's the idempotency
-- key a retried/duplicated webhook delivery is deduped against (see
-- PaymentService.handleCheckoutSessionCompleted) — the Checkout Session
-- object it names is created exactly once per checkout attempt and only
-- this table's row for it is ever updated, never re-inserted.
CREATE TABLE payments
(
    id                       UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    -- RESTRICT, not CASCADE — same reasoning as bookings.showtime_id (V9):
    -- a payment record is financial history and must never be silently
    -- destroyed by deleting the booking it belongs to. There is no booking
    -- delete API today, so this can't yet be exercised, but the FK is
    -- correct from day one rather than patched in later (the V7 lesson).
    booking_id               UUID           NOT NULL REFERENCES bookings (id) ON DELETE RESTRICT,
    stripe_session_id        VARCHAR(255)   NOT NULL UNIQUE,
    stripe_payment_intent_id VARCHAR(255),
    amount                   NUMERIC(10, 2) NOT NULL,
    currency                 VARCHAR(3)     NOT NULL,
    status                   VARCHAR(20)    NOT NULL,
    created_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT chk_payments_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_payments_amount CHECK (amount >= 0)
);

CREATE INDEX idx_payments_booking_id ON payments (booking_id);
