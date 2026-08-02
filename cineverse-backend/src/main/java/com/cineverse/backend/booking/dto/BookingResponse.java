package com.cineverse.backend.booking.dto;

import com.cineverse.backend.booking.entity.BookingStatus;
import com.cineverse.backend.cinema.entity.SeatType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        BookingStatus status,
        @Schema(example = "50.00") BigDecimal totalPrice,
        @Schema(description = "5 分钟持有窗口的到期时间；PENDING 状态下超过此时间会在下一次读取时被懒惰标记为 EXPIRED")
        Instant expiresAt,
        Instant createdAt,
        ShowtimeSummary showtime,
        List<BookingSeatResponse> seats) {

    public record ShowtimeSummary(
            UUID id,
            @Schema(example = "Interstellar") String movieTitle,
            @Schema(example = "Hall 1") String hallName,
            Instant startTime) {
    }

    public record BookingSeatResponse(
            UUID seatId,
            @Schema(example = "A") String rowLabel,
            @Schema(example = "1") Integer columnNumber,
            SeatType seatType,
            @Schema(example = "25.00", description = "下单当时的座位价格；showtime 之后调价不会影响历史订单")
            BigDecimal priceAtBooking) {
    }
}
