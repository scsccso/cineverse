package com.cineverse.backend.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID showtimeId,

        @NotEmpty
        @Schema(description = "至少 1 个座位；每个座位在提交前都会先用 Redis 原子加锁，"
                + "全部成功才创建订单，任何一个失败则整体回滚") List<@NotNull UUID> seatIds) {
}
