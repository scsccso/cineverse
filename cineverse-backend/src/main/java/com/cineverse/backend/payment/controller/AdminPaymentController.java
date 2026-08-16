package com.cineverse.backend.payment.controller;

import com.cineverse.backend.payment.dto.AdminPaymentResponse;
import com.cineverse.backend.payment.entity.PaymentStatus;
import com.cineverse.backend.payment.service.AdminPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/payments")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Payments", description = "支付记录只读查询,仅 ADMIN——人工对账的第一步(找到是哪几笔),"
        + "不提供任何退款/状态修改操作,那部分仍然是运营层面的人工流程")
public class AdminPaymentController {

    private final AdminPaymentService adminPaymentService;

    public AdminPaymentController(AdminPaymentService adminPaymentService) {
        this.adminPaymentService = adminPaymentService;
    }

    @GetMapping
    @Operation(summary = "分页查询支付记录", description = "按状态筛选(可选,不传则返回全部状态),"
            + "每条记录带关联的 booking/用户邮箱/场次信息(均从 payments 表已有的外键关联查出,"
            + "没有新增字段)。最典型的用法是 status=ORPHANED_SUCCESS——销售报表已经把这个状态的"
            + "汇总金额单独展示为 pendingReconciliationAmount,这个接口是那个数字的明细来源")
    public Page<AdminPaymentResponse> search(
            @RequestParam(required = false) PaymentStatus status, Pageable pageable) {
        return adminPaymentService.search(status, pageable);
    }
}
