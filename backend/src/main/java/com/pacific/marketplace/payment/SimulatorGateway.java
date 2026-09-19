package com.pacific.marketplace.payment;

import com.pacific.marketplace.domain.PaymentProviderType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Local-development stand-in for a payment provider: the "hosted page" is a clearly labelled test page inside the
 * storefront. It never sees card details and never moves money. Enabled only by app.payments.simulator-enabled.
 */
public class SimulatorGateway implements PaymentGateway {

    private final String publicUrl;

    public SimulatorGateway(String publicUrl) {
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.SIMULATOR;
    }

    @Override
    public GatewaySession create(CheckoutSpec spec) {
        return new GatewaySession("sim_" + spec.ref(), publicUrl + "/pay/simulate/" + spec.ref());
    }

    @Override
    public GatewayStatus status(String sessionId) {
        return new GatewayStatus(State.OPEN, null); // the simulator's outcome is driven by the test page, not polled
    }

    @Override
    public void expire(String sessionId) {
        // nothing to close
    }

    @Override
    public String refund(String paymentRef, BigDecimal amount, String currency, String idempotencyKey) {
        return "sim_refund_" + UUID.randomUUID();
    }
}
