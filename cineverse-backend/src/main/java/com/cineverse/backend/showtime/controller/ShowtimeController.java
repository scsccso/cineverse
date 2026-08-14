package com.cineverse.backend.showtime.controller;

import com.cineverse.backend.booking.dto.ShowtimeSeatsResponse;
import com.cineverse.backend.booking.service.BookingService;
import com.cineverse.backend.showtime.dto.CreateShowtimeRequest;
import com.cineverse.backend.showtime.dto.ShowtimeResponse;
import com.cineverse.backend.showtime.service.ShowtimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/showtimes")
@Tag(name = "Showtimes", description = "浏览公开,只读;新增/删除仅 ADMIN,不支持更新(排期错了删除重建)")
public class ShowtimeController {

    private final ShowtimeService showtimeService;
    private final BookingService bookingService;

    public ShowtimeController(ShowtimeService showtimeService, BookingService bookingService) {
        this.showtimeService = showtimeService;
        this.bookingService = bookingService;
    }

    @GetMapping
    @Operation(summary = "按电影/影厅/日期查询场次", description = "公开接口,无需登录;movieId、hallId、date 都可选,"
            + "date 按 UTC 自然日筛选(如 2026-08-10)。响应同时带 bookedSeats/totalSeats"
            + "(仅统计 CONFIRMED 订单,和管理后台上座率报表同一口径),不分页——见"
            + "CLAUDE.md「Admin 场次管理」一节为什么这个公开端点没有改成 Page<T>")
    public List<ShowtimeResponse> list(
            @RequestParam(required = false) UUID movieId,
            @RequestParam(required = false) UUID hallId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return showtimeService.list(movieId, hallId, date);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取场次详情", description = "公开接口,无需登录;含电影/影厅基本信息,减少前端拼数据的请求")
    public ShowtimeResponse getById(@PathVariable UUID id) {
        return showtimeService.getById(id);
    }

    @GetMapping("/{id}/seats")
    @Operation(summary = "查询场次座位状态", description = "公开接口,无需登录;选座页轮询用(MVP阶段用轮询,不用WebSocket)。"
            + "每个座位除了静态布局信息(row/column/columnSpan/type),还带该场次下的动态状态"
            + "(AVAILABLE/LOCKED/BOOKED);读取时若发现某座位对应的 PENDING booking 已过期,"
            + "会懒惰标记为 EXPIRED 后再返回最新状态")
    public ShowtimeSeatsResponse getSeats(@PathVariable UUID id) {
        return bookingService.getShowtimeSeats(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "创建场次", description = "仅 ADMIN;结束时间由开始时间 + 电影时长自动计算,不接受手动指定;"
            + "同一影厅的场次之间必须间隔至少 20 分钟清场缓冲,冲突返回 409 并说明是和哪个已有场次冲突")
    public ShowtimeResponse create(@Valid @RequestBody CreateShowtimeRequest request) {
        return showtimeService.create(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "删除场次", description = "仅 ADMIN;没有更新 API,排期错了删除重建")
    public void delete(@PathVariable UUID id) {
        showtimeService.delete(id);
    }
}
