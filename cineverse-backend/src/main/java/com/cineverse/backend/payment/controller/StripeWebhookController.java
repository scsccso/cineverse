package com.cineverse.backend.payment.controller;

import com.cineverse.backend.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by Stripe's servers, not a browser — public (see SecurityConfig),
 * with the Stripe-Signature header standing in for authentication. The raw
 * request body is bound as a plain String (not a parsed DTO): signature
 * verification is an HMAC over the exact bytes Stripe sent, so anything that
 * reserializes the JSON first (even with identical field values) would break
 * it.
 */
@RestController
@RequestMapping("/api/v1/webhooks/stripe")
@Tag(name = "Stripe Webhooks", description = "Stripe 服务器回调,不需要认证,但会校验 Stripe-Signature 请求头")
public class StripeWebhookController {

    private final PaymentService paymentService;

    public StripeWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Stripe webhook 入口", description = "验证签名后处理 checkout.session.completed"
            + "(幂等地把 booking 从 PENDING 转为 CONFIRMED)和 checkout.session.expired"
            + "(记录支付失败,booking 状态交给 Phase 5 的懒惰过期机制处理);其余事件类型直接返回 200 但不处理")
    public void handleWebhook(
            @RequestBody String payload, @RequestHeader("Stripe-Signature") String signatureHeader) {
        paymentService.handleWebhookEvent(payload, signatureHeader);
    }
}
