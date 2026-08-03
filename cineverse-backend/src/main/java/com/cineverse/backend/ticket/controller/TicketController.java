package com.cineverse.backend.ticket.controller;

import com.cineverse.backend.ticket.dto.RedeemTicketRequest;
import com.cineverse.backend.ticket.dto.TicketRedemptionResponse;
import com.cineverse.backend.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Tickets", description = "入场核销(Phase 7);仅 ADMIN,没有专门的扫码 UI——" +
        "接受扫码得到的原始字符串或工作人员手动输入的编码,票据编码本身是签名过的 JWT,见 TicketCodeService")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/redeem")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "核销电子票(入场检票)", description = "校验票据编码的签名、booking 是否 CONFIRMED、"
            + "是否已经核销过;三项都通过才标记为已核销并返回场次/座位信息供工作人员核对。"
            + "签名不对返回 400,booking 未支付/已过期等非 CONFIRMED 状态或已经核销过都返回 409")
    public TicketRedemptionResponse redeem(@Valid @RequestBody RedeemTicketRequest request) {
        return ticketService.redeem(request.ticketCode());
    }
}
