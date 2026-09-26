package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacific.marketplace.domain.Product;
import com.stripe.net.Webhook;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Stripe's webhook: only correctly signed events count, and only for what we actually asked to be paid. */
@TestPropertySource(properties = "app.payments.stripe.webhook-secret=whsec_test_secret")
class StripeWebhookTest extends PaymentTestBase {

    private static final String SECRET = "whsec_test_secret";

    private String event(String type, String ref, String paymentStatus, long amountMinor, String currency)
            throws Exception {
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("id", "cs_test_1");
        session.put("client_reference_id", ref);
        session.put("payment_status", paymentStatus);
        session.put("payment_intent", "pi_test_1");
        session.put("amount_total", amountMinor);
        session.put("currency", currency);
        return json.writeValueAsString(Map.of("id", "evt_1", "type", type, "data", Map.of("object", session)));
    }

    private ResultActions deliver(String payload, String signatureHeader) throws Exception {
        var request = post("/api/payments/webhook").contentType("application/json").content(payload);
        if (signatureHeader != null) request = request.header("Stripe-Signature", signatureHeader);
        return mvc.perform(request);
    }

    private ResultActions deliverSigned(String payload) throws Exception {
        return deliver(payload, Webhook.Signature.generateSignatureHeader(payload, SECRET));
    }

    private JsonNode pendingCheckout(String token, Product p) throws Exception {
        addToCart(token, p.getId(), 1);
        return cardCheckout(token);
    }

    @Test
    void aSignedPaidEventPlacesTheOrders() throws Exception {
        String token = registerCustomer();
        Product p = product("Chair", "30.00", 3);
        JsonNode res = pendingCheckout(token, p);
        String ref = res.get("checkoutRef").asText();
        long minor = res.get("total").decimalValue().movePointRight(2).longValueExact();

        deliverSigned(event("checkout.session.completed", ref, "paid", minor, "gbp")).andExpect(status().isOk());
        assertThat(payment(token, ref, 200).get("status").asText()).isEqualTo("PAID");
        assertThat(order(token, res.get("orders").get(0).get("id").asLong()).get("status").asText())
                .isEqualTo("PLACED");

        // Stripe re-sends events; a repeat must change nothing
        deliverSigned(event("checkout.session.completed", ref, "paid", minor, "gbp")).andExpect(status().isOk());
        deliverSigned(event("checkout.session.async_payment_succeeded", ref, "paid", minor, "gbp"))
                .andExpect(status().isOk());
        assertThat(payment(token, ref, 200).get("refundedAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(stockOf(p)).isEqualTo(2);
    }

    @Test
    void unsignedOrBadlySignedEventsAreRejectedAndChangeNothing() throws Exception {
        String token = registerCustomer();
        Product p = product("Desk", "30.00", 3);
        JsonNode res = pendingCheckout(token, p);
        String ref = res.get("checkoutRef").asText();
        long minor = res.get("total").decimalValue().movePointRight(2).longValueExact();
        String payload = event("checkout.session.completed", ref, "paid", minor, "gbp");

        deliver(payload, null).andExpect(status().isBadRequest());
        deliver(payload, "garbage").andExpect(status().isBadRequest());
        deliver(payload, Webhook.Signature.generateSignatureHeader(payload, "whsec_someone_elses"))
                .andExpect(status().isBadRequest());
        // a genuine signature for a different body doesn't cover this one
        String other = Webhook.Signature.generateSignatureHeader(payload + " ", SECRET);
        deliver(payload, other).andExpect(status().isBadRequest());
        // an old (replayed) signature is refused too
        deliver(payload, Webhook.Signature.generateSignatureHeader(payload, SECRET, 1_000_000_000L))
                .andExpect(status().isBadRequest());

        assertThat(payment(token, ref, 200).get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void aPaymentForADifferentAmountIsRefundedAndTheCheckoutReleased() throws Exception {
        String token = registerCustomer();
        Product p = product("Shelf", "30.00", 3);
        JsonNode res = pendingCheckout(token, p);
        String ref = res.get("checkoutRef").asText();
        long minor = res.get("total").decimalValue().movePointRight(2).longValueExact();

        // A signed event is real money: it's refunded as paid, and the orders aren't placed (the email is checked in
        // PaymentRefundEmailTest, which commits).
        deliverSigned(event("checkout.session.completed", ref, "paid", minor - 100, "gbp")).andExpect(status().isOk());
        assertThat(payment(token, ref, 200).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(order(token, res.get("orders").get(0).get("id").asLong()).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(stockOf(p)).isEqualTo(3);

        // A repeat (or the same money reported in another currency) doesn't place the orders either.
        deliverSigned(event("checkout.session.completed", ref, "paid", minor, "usd")).andExpect(status().isOk());
        assertThat(payment(token, ref, 200).get("status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void anUnpaidCompletionAndUnrelatedEventsAreIgnored() throws Exception {
        String token = registerCustomer();
        Product p = product("Stool", "30.00", 3);
        JsonNode res = pendingCheckout(token, p);
        String ref = res.get("checkoutRef").asText();
        long minor = res.get("total").decimalValue().movePointRight(2).longValueExact();

        // delayed payment methods complete the session before the money arrives
        deliverSigned(event("checkout.session.completed", ref, "unpaid", minor, "gbp")).andExpect(status().isOk());
        deliverSigned(event("charge.refunded", ref, "paid", minor, "gbp")).andExpect(status().isOk());
        deliverSigned(event("checkout.session.completed", "no-such-checkout", "paid", minor, "gbp"))
                .andExpect(status().isOk());
        assertThat(payment(token, ref, 200).get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void anExpiredSessionEventReleasesTheStock() throws Exception {
        String token = registerCustomer();
        Product p = product("Rug", "30.00", 3);
        JsonNode res = pendingCheckout(token, p);
        String ref = res.get("checkoutRef").asText();
        assertThat(stockOf(p)).isEqualTo(2);

        deliverSigned(event("checkout.session.expired", ref, "unpaid", 0, "gbp")).andExpect(status().isOk());
        assertThat(payment(token, ref, 200).get("status").asText()).isEqualTo("EXPIRED");
        assertThat(stockOf(p)).isEqualTo(3);
    }

    @Test
    void aPaidEventAfterExpiryIsRefunded() throws Exception {
        String token = registerCustomer();
        Product p = product("Lamp", "30.00", 3);
        JsonNode res = pendingCheckout(token, p);
        String ref = res.get("checkoutRef").asText();
        long minor = res.get("total").decimalValue().movePointRight(2).longValueExact();
        deliverSigned(event("checkout.session.expired", ref, "unpaid", 0, "gbp")).andExpect(status().isOk());

        deliverSigned(event("checkout.session.completed", ref, "paid", minor, "gbp")).andExpect(status().isOk());
        JsonNode after = payment(token, ref, 200);
        assertThat(after.get("refundedAmount").decimalValue()).isEqualByComparingTo(after.get("amount").decimalValue());
        assertThat(stockOf(p)).isEqualTo(3);
    }

}
