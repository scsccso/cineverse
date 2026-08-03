package com.cineverse.backend.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record CheckoutSessionResponse(
        @Schema(description = "Stripe 托管支付页面的 URL,前端拿到后整页跳转过去(window.location.href,不需要 Stripe.js)")
        String checkoutUrl) {
}
