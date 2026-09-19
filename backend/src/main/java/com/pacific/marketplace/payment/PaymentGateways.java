package com.pacific.marketplace.payment;

import com.pacific.marketplace.config.AppProperties;
import com.pacific.marketplace.domain.PaymentProviderType;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Chooses the payment provider from configuration: Stripe if a key is set, else the simulator if enabled, else none. */
@Component
public class PaymentGateways {

    private static final Logger log = LoggerFactory.getLogger(PaymentGateways.class);

    private final StripeGateway stripe;
    private final SimulatorGateway simulator;

    public PaymentGateways(AppProperties props) {
        AppProperties.Payments cfg = props.payments();
        this.stripe = cfg.stripe().configured() ? new StripeGateway(cfg.stripe().secretKey(), cfg.stripe().apiBase()) : null;
        this.simulator = cfg.simulatorEnabled() ? new SimulatorGateway(props.publicUrl()) : null;
        if (stripe != null && cfg.stripe().secretKey().startsWith("sk_live") && simulator != null) {
            throw new IllegalStateException("Refusing to start with a live Stripe key and the payment simulator both enabled.");
        }
        if (stripe != null) log.info("Card payments: Stripe enabled.");
        else if (simulator != null) log.warn("Card payments: TEST-MODE SIMULATOR enabled (no real money). Never use in production.");
        else log.info("Card payments: disabled (pay on delivery only).");
    }

    /** The provider new card payments use. */
    public Optional<PaymentGateway> active() {
        return Optional.ofNullable(stripe != null ? stripe : simulator);
    }

    public Optional<StripeGateway> stripe() {
        return Optional.ofNullable(stripe);
    }

    public boolean simulatorActive() {
        return stripe == null && simulator != null;
    }

    /** The gateway that created a given payment (a payment keeps working even if configuration changes later). */
    public Optional<PaymentGateway> forType(PaymentProviderType type) {
        return type == PaymentProviderType.STRIPE ? Optional.ofNullable(stripe) : Optional.ofNullable(simulator);
    }
}
