package com.cineverse.backend.booking.controller;

import com.cineverse.backend.booking.dto.AdminBookingSearchResult;
import com.cineverse.backend.booking.entity.BookingStatus;
import com.cineverse.backend.booking.service.AdminBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/bookings")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Bookings", description = "客服/后台订单查询,仅 ADMIN;不新增取消/详情接口,"
        + "复用已有的 GET/DELETE /api/v1/bookings/{id}(两者本来就支持 ADMIN 越权访问)")
public class AdminBookingController {

    private final AdminBookingService adminBookingService;

    public AdminBookingController(AdminBookingService adminBookingService) {
        this.adminBookingService = adminBookingService;
    }

    @GetMapping
    @Operation(summary = "搜索订单", description = "按用户邮箱(模糊匹配)、电影标题(模糊匹配)、订单状态(精确匹配)"
            + "分页查询全部用户的订单,三个条件都可选、可任意组合,全部为空则等同分页浏览全部订单。"
            + "读取时对已过 expires_at 但仍是 PENDING 的订单批量做懒惰过期,规则与 GET /bookings/{id} 一致")
    public Page<AdminBookingSearchResult> search(
            @Parameter(description = "大小写不敏感,模糊匹配") @RequestParam(required = false) String userEmail,
            @Parameter(description = "大小写不敏感,模糊匹配") @RequestParam(required = false) String movieTitle,
            @RequestParam(required = false) BookingStatus status,
            Pageable pageable) {
        return adminBookingService.search(userEmail, movieTitle, status, pageable);
    }
}
