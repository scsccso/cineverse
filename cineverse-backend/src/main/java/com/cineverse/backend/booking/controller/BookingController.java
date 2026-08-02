package com.cineverse.backend.booking.controller;

import com.cineverse.backend.booking.dto.BookingResponse;
import com.cineverse.backend.booking.dto.CreateBookingRequest;
import com.cineverse.backend.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Bookings", description = "选座/订票；需要登录(CUSTOMER/ADMIN都行，不限角色)，"
        + "只有本人或ADMIN能查看/取消自己的订票")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "锁座并创建订单", description = "对请求里的每个座位做 Redis 原子加锁，全部成功才创建 "
            + "PENDING 状态的 booking(5 分钟持有窗口);任何一个座位加锁失败，已加锁的会全部释放，"
            + "返回 409 并说明具体是哪个座位已被占用，不留下任何数据库记录")
    public BookingResponse create(Authentication authentication, @Valid @RequestBody CreateBookingRequest request) {
        return bookingService.create(currentUserId(authentication), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "主动放弃选座", description = "释放对应的 Redis 座位锁，booking 状态改为 CANCELLED；"
            + "只有本人或 ADMIN 能取消")
    public void cancel(Authentication authentication, @PathVariable UUID id) {
        bookingService.cancel(currentUserId(authentication), isAdmin(authentication), id);
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "查看订单详情", description = "只有本人或 ADMIN 能查看；读取时若发现该 booking 已过 "
            + "expires_at 但仍是 PENDING，会在此次请求中懒惰标记为 EXPIRED 再返回")
    public BookingResponse getById(Authentication authentication, @PathVariable UUID id) {
        return bookingService.getById(currentUserId(authentication), isAdmin(authentication), id);
    }

    private UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
