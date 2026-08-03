package com.cineverse.backend.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RedeemTicketRequest(
        @NotBlank
        @Schema(description = "从 QR code 扫描出来的原始字符串,或者工作人员手动输入的票据编码")
        String ticketCode) {
}
