package com.cineverse.backend.demo.controller;

import com.cineverse.backend.demo.dto.DemoResetResult;
import com.cineverse.backend.demo.service.DemoResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Not under /api/v1/** and not JWT-protected — a scheduled cron calling
 * this has no admin session to manage, so it authenticates with a single
 * shared secret header instead, the same pattern StripeWebhookController
 * uses for a caller that isn't a browser either (there: Stripe-Signature;
 * here: X-Demo-Reset-Secret). SecurityConfig permitAll's /internal/** for
 * exactly this reason — DemoResetService.verifySecret is the entire access
 * control, not a Spring Security matcher. See docs/DEPLOYMENT.md for how to
 * configure and schedule calls to these.
 */
@RestController
@RequestMapping("/internal/demo-reset")
@Tag(name = "Internal — Demo Reset", description = "共享密钥保护(X-Demo-Reset-Secret 请求头),不走 JWT——"
        + "只在配置了 DEMO_RESET_SECRET 的环境下可用,未配置时任何请求都返回 503")
public class DemoResetController {

    private static final String SECRET_HEADER = "X-Demo-Reset-Secret";

    private final DemoResetService demoResetService;

    public DemoResetController(DemoResetService demoResetService) {
        this.demoResetService = demoResetService;
    }

    @PostMapping("/transactions")
    @Operation(summary = "清空交易数据(顾客产生的订单/支付/座位锁),不动场次排期",
            description = "适合高频调度(如每 6 小时)——只处理 bookings/payments/booking_seats 和 Redis 座位锁,"
                    + "场次本身不受影响")
    public DemoResetResult resetTransactions(
            @RequestHeader(name = SECRET_HEADER, required = false) String secret) {
        demoResetService.verifySecret(secret);
        return demoResetService.resetTransactions();
    }

    @PostMapping("/showtimes")
    @Operation(summary = "完整重置:清空交易数据 + 重新生成未来场次排期",
            description = "适合低频调度(如每天一次)——先做一遍 .../transactions 同样的交易数据清理"
                    + "(场次的 RESTRICT 外键要求订单先清空),再删除全部现有场次并按固定模板重新生成未来一周")
    public DemoResetResult resetShowtimes(
            @RequestHeader(name = SECRET_HEADER, required = false) String secret) {
        demoResetService.verifySecret(secret);
        return demoResetService.resetShowtimes();
    }
}
