package com.pacific.marketplace.payment;

import com.pacific.marketplace.domain.PaymentProviderType;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import java.math.BigDecimal;
import java.util.Locale;

/** Stripe Checkout (hosted page). The customer's card details go to Stripe, never to us. */
public class StripeGateway implements PaymentGateway {

    private final StripeClient client;

    public StripeGateway(String secretKey, String apiBase) {
        StripeClient.StripeClientBuilder builder = StripeClient.builder().setApiKey(secretKey)
                .setConnectTimeout(5_000).setReadTimeout(20_000);
        if (apiBase != null && !apiBase.isBlank()) builder.setApiBase(apiBase); // used by tests to point at a stub
        this.client = builder.build();
    }

    @Override
    public PaymentProviderType type() {
        return PaymentProviderType.STRIPE;
    }

    @Override
    public GatewaySession create(CheckoutSpec spec) {
        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(spec.successUrl())
                .setCancelUrl(spec.cancelUrl())
                .setClientReferenceId(spec.ref())
                .setExpiresAt(spec.expiresAt().getEpochSecond())
                .putMetadata("checkout_ref", spec.ref())
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .putMetadata("checkout_ref", spec.ref()).build());
        if (spec.customerEmail() != null) params.setCustomerEmail(spec.customerEmail());
        String currency = spec.currency().toLowerCase(Locale.ROOT);
        for (Line line : spec.lines()) {
            params.addLineItem(SessionCreateParams.LineItem.builder()
                    .setQuantity(line.quantity())
                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(currency)
                            .setUnitAmount(line.unitAmountMinor())
                            .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(line.name()).build())
                            .build())
                    .build());
        }
        try {
            // The idempotency key makes a retried request return the same session instead of creating a second one.
            Session session = client.checkout().sessions().create(params.build(),
                    RequestOptions.builder().setIdempotencyKey("pacific-checkout-" + spec.ref()).build());
            return new GatewaySession(session.getId(), session.getUrl());
        } catch (StripeException e) {
            throw new GatewayException("Stripe could not start the payment: " + e.getMessage(), e);
        }
    }

    @Override
    public GatewayStatus status(String sessionId) {
        try {
            Session s = client.checkout().sessions().retrieve(sessionId);
            if ("paid".equals(s.getPaymentStatus())) return new GatewayStatus(State.PAID, s.getPaymentIntent());
            if ("expired".equals(s.getStatus())) return new GatewayStatus(State.EXPIRED, null);
            return new GatewayStatus(State.OPEN, null);
        } catch (StripeException e) {
            throw new GatewayException("Stripe could not be reached: " + e.getMessage(), e);
        }
    }

    @Override
    public void expire(String sessionId) {
        try {
            client.checkout().sessions().expire(sessionId);
        } catch (StripeException e) {
            throw new GatewayException("Could not close the Stripe session: " + e.getMessage(), e);
        }
    }

    @Override
    public String refund(String paymentRef, BigDecimal amount, String currency, String idempotencyKey) {
        try {
            return client.refunds().create(RefundCreateParams.builder().setPaymentIntent(paymentRef)
                            .setAmount(amount.movePointRight(2).longValueExact()).build(),
                    RequestOptions.builder().setIdempotencyKey(idempotencyKey).build()).getId();
        } catch (StripeException e) {
            throw new GatewayException("Stripe could not refund the payment: " + e.getMessage(), e);
        }
    }
}
