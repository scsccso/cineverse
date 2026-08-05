package com.cineverse.backend.payment.gateway;

import com.cineverse.backend.payment.config.StripeProperties;
import com.cineverse.backend.payment.exception.StripeGatewayException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Real Stripe API implementation. The secret key is passed per-call via
 * RequestOptions rather than the SDK's global static {@code Stripe.apiKey}
 * field — avoids mutating shared static state from a Spring bean.
 */
@Slf4j
@Component
public class StripeCheckoutGatewayImpl implements StripeCheckoutGateway {

    private final StripeProperties properties;

    public StripeCheckoutGatewayImpl(StripeProperties properties) {
        this.properties = properties;
        // Presence/length only — never the value itself. app.stripe.secret-key
        // and .webhook-secret have no dev-only fallback (see StripeProperties),
        // so an unset env var silently resolves to "" here instead of failing
        // at startup; this line is the fastest way to tell "not loaded" apart
        // from "loaded but rejected by Stripe" without ever printing a secret.
        log.info(
                "Stripe config loaded: secretKey present={} (length={}), webhookSecret present={} (length={})",
                !properties.secretKey().isBlank(), properties.secretKey().length(),
                !properties.webhookSecret().isBlank(), properties.webhookSecret().length());
    }

    @Override
    public CreatedCheckoutSession createSession(CheckoutSessionSpec spec) {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(spec.successUrl())
                .setCancelUrl(spec.cancelUrl())
                .setClientReferenceId(spec.bookingId().toString())
                .putMetadata("bookingId", spec.bookingId().toString())
                .setExpiresAt(spec.expiresAt().getEpochSecond())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(spec.currency())
                                .setUnitAmount(toMinorUnits(spec.amount()))
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(spec.productName())
                                        .build())
                                .build())
                        .build())
                .build();

        RequestOptions requestOptions =
                RequestOptions.builder().setApiKey(properties.secretKey()).build();

        try {
            Session session = Session.create(params, requestOptions);
            return new CreatedCheckoutSession(session.getId(), session.getUrl());
        } catch (StripeException e) {
            throw new StripeGatewayException("Failed to create Stripe Checkout Session", e);
        }
    }

    @Override
    public void expireSession(String stripeSessionId) {
        RequestOptions requestOptions =
                RequestOptions.builder().setApiKey(properties.secretKey()).build();
        try {
            Session session = Session.retrieve(stripeSessionId, requestOptions);
            session.expire(requestOptions);
        } catch (StripeException e) {
            throw new StripeGatewayException("Failed to expire Stripe Checkout Session " + stripeSessionId, e);
        }
    }

    /** Stripe amounts are in the smallest currency unit (e.g. sen for MYR) — always 2 decimal places for the currencies this app uses. */
    private long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
