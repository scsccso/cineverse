-- Phase 7: e-ticket check-in. A single nullable timestamp does double duty
-- as both "has this ticket been redeemed" and "when" — NULL means not yet
-- redeemed, a non-null value is the redemption instant. Deliberately not a
-- separate boolean + timestamp pair: that shape allows an invalid state
-- (redeemed = true, redeemed_at = null, or the reverse) that this doesn't.
-- No new "tickets" table — a ticket is just a CONFIRMED booking viewed
-- through a different lens for admission purposes, not a separate entity
-- with its own lifecycle; the QR/ticket code itself is a signed JWT
-- (booking id as subject, see TicketCodeService) computed on the fly and
-- never persisted, since it's fully deterministic from the booking id plus
-- the server-side signing secret.
ALTER TABLE bookings
    ADD COLUMN redeemed_at TIMESTAMPTZ NULL;
