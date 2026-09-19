package com.pacific.marketplace.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacific.marketplace.config.AppProperties;
import com.pacific.marketplace.service.PaymentService;
import com.pacific.marketplace.web.ApiException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Receives Stripe's events. Nothing in a request is trusted until its signature is checked against the webhook secret;
 * after that only the session's reference, payment status, amount and currency are read from the JSON (parsed
 * directly, so it doesn't matter which Stripe API version the account uses).
 */
@Component
public class StripeWebhook {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhook.class);

    private final AppProperties props;
    private final ObjectMapper json;
    private final PaymentService payments;

    public StripeWebhook(AppProperties props, ObjectMapper json, PaymentService payments) {
        this.props = props;
        this.json = json;
        this.payments = payments;
    }

    public void handle(byte[] body, String signatureHeader) {
        String secret = props.payments().stripe().webhookSecret();
        if (secret == null || secret.isBlank()) throw ApiException.notFound("Not found.");

        String payload = new String(body, StandardCharsets.UTF_8);
        try {
            if (signatureHeader == null || !Webhook.Signature.verifyHeader(payload, signatureHeader, secret,
                    Webhook.DEFAULT_TOLERANCE)) {
                throw ApiException.badRequest("Invalid signature.");
            }
        } catch (SignatureVerificationException e) {
            throw ApiException.badRequest("Invalid signature.");
        }

        JsonNode event;
        try {
            event = json.readTree(payload);
        } catch (IOException e) {
            throw ApiException.badRequest("Malformed event.");
        }
        String type = event.path("type").asText("");
        JsonNode session = event.path("data").path("object");
        String ref = session.path("client_reference_id").asText(session.path("metadata").path("checkout_ref").asText(""));
        if (ref.isBlank()) {
            log.debug("Ignoring Stripe event {} without a checkout reference", type);
            return;
        }

        switch (type) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> {
                if ("paid".equals(session.path("payment_status").asText(""))) {
                    Long amount = session.path("amount_total").isNumber() ? session.path("amount_total").asLong() : null;
                    payments.markPaid(ref, session.path("payment_intent").asText(null), amount,
                            session.path("currency").asText(null));
                }
            }
            case "checkout.session.expired" -> payments.expire(ref);
            default -> log.debug("Ignoring Stripe event {}", type);
        }
    }
}
