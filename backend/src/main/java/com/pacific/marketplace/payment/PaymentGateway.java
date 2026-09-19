package com.pacific.marketplace.payment;

import com.pacific.marketplace.domain.PaymentProviderType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A hosted-checkout payment provider. Card details never reach our servers: the customer pays on the provider's page. */
public interface PaymentGateway {

    PaymentProviderType type();

    /** Creates a hosted checkout session the customer is redirected to. */
    GatewaySession create(CheckoutSpec spec);

    /** Asks the provider what happened to a session (used when a webhook is late or can't reach us). */
    GatewayStatus status(String sessionId);

    /** Best effort: stops the customer paying on a session we are about to cancel. Throws if it can't be closed. */
    void expire(String sessionId);

    /** Refunds part or all of a paid payment; returns the provider's refund id. */
    String refund(String paymentRef, BigDecimal amount, String currency, String idempotencyKey);

    record Line(String name, long unitAmountMinor, long quantity) {
    }

    record CheckoutSpec(String ref, String customerEmail, String currency, List<Line> lines, Instant expiresAt,
                        String successUrl, String cancelUrl) {
    }

    record GatewaySession(String sessionId, String url) {
    }

    enum State { OPEN, PAID, EXPIRED }

    record GatewayStatus(State state, String paymentRef) {
    }

    class GatewayException extends RuntimeException {
        public GatewayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
