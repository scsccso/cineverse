package com.cineverse.backend.booking.service;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed mutual exclusion for concurrent seat-booking attempts.
 * Expiry relies entirely on Redis's own TTL (EXPIRE under the hood) instead
 * of any application-level polling — more reliable, and nothing to clean up
 * on our side even if the app crashes mid-hold.
 */
@Service
public class SeatLockService {

    private final StringRedisTemplate redisTemplate;

    public SeatLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Atomic {@code SET key value NX EX ttl} — a single Redis command, not a
     * GET-then-SET pair, so two concurrent callers can never both observe
     * "unlocked" and both proceed. {@code holderId} is the acquiring user's
     * ID rather than a bare flag, so a lock can be traced back to who holds
     * it instead of just "something has this seat".
     *
     * @return true iff this call acquired the lock (false means someone else already holds it)
     */
    public boolean tryLock(UUID showtimeId, UUID seatId, String holderId, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key(showtimeId, seatId), holderId, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public void unlock(UUID showtimeId, UUID seatId) {
        redisTemplate.delete(key(showtimeId, seatId));
    }

    /**
     * Refreshes an already-held lock's TTL — a plain {@code EXPIRE}, not
     * another {@code SETNX}. Safe without re-checking the holder because the
     * only caller (PaymentService, when checkout begins) has already
     * verified the booking is still PENDING and owned by the caller, which
     * is only possible if this lock is the one that booking's own
     * BookingService.create() acquired in the first place.
     */
    public void extend(UUID showtimeId, UUID seatId, Duration ttl) {
        redisTemplate.expire(key(showtimeId, seatId), ttl);
    }

    private String key(UUID showtimeId, UUID seatId) {
        return "seat-lock:%s:%s".formatted(showtimeId, seatId);
    }
}
