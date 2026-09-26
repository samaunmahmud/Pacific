package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacific.marketplace.domain.SentEmail;
import com.stripe.net.Webhook;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Card payments the shop couldn't use are refunded, and the customer is told by email (these need real commits). */
@TestPropertySource(properties = "app.payments.stripe.webhook-secret=whsec_refund_email_secret")
class PaymentRefundEmailTest extends CommittedFlowTestBase {

    private List<SentEmail> refundEmails(Account who) {
        return emailsTo(who.email()).stream().filter(e -> e.getKind().equals("PAYMENT_REFUNDED")).toList();
    }

    private JsonNode unpaidCardCheckout(Account buyer, long productId) throws Exception {
        addToCart(buyer, productId, 1);
        return checkout(buyer, "CARD", 201);
    }

    @Test
    void aPaymentForTheWrongAmountIsRefundedAndTheCustomerToldWhy() throws Exception {
        Account seller = seller();
        long product = listProduct(seller, "Walnut Tray", "24.00", 5);
        Account buyer = customer();
        JsonNode res = unpaidCardCheckout(buyer, product);
        String ref = res.get("checkoutRef").asText();
        long minor = res.get("total").decimalValue().movePointRight(2).longValueExact();

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("client_reference_id", ref);
        session.put("payment_status", "paid");
        session.put("payment_intent", "pi_mismatch");
        session.put("amount_total", minor - 250);
        session.put("currency", "gbp");
        String payload = json.writeValueAsString(Map.of("id", "evt_m", "type", "checkout.session.completed",
                "data", Map.of("object", session)));
        mvc.perform(post("/api/payments/webhook").contentType("application/json").content(payload)
                .header("Stripe-Signature", Webhook.Signature.generateSignatureHeader(payload, "whsec_refund_email_secret")))
                .andExpect(status().isOk());

        List<SentEmail> emails = refundEmails(buyer);
        assertThat(emails).hasSize(1);
        assertThat(emails.get(0).getSubject()).isEqualTo("Your Pacific payment has been refunded");
        assertThat(emails.get(0).getBody()).contains("£" + BigDecimal.valueOf(minor - 250).movePointLeft(2))
                .contains("couldn't match your card payment");
        // No order confirmation went out, and the items are back in the cart.
        assertThat(emailsTo(buyer.email())).noneMatch(e -> e.getKind().equals("ORDER_CONFIRMATION"));
        assertThat(send(get("/api/cart"), buyer.token(), null, 200).get("items")).hasSize(1);
    }

    @Test
    void aPaymentThatArrivesAfterTheCheckoutWasCancelledIsRefundedWithAnEmail() throws Exception {
        Account seller = seller();
        long product = listProduct(seller, "Linen Napkins", "16.00", 5);
        Account buyer = customer();
        String ref = unpaidCardCheckout(buyer, product).get("checkoutRef").asText();
        send(post("/api/payments/" + ref + "/cancel"), buyer.token(), null, 200);

        // The customer completes the payment page anyway, a moment too late.
        send(post("/api/payments/" + ref + "/simulate"), buyer.token(), Map.of("outcome", "PAID"), 200);

        List<SentEmail> emails = refundEmails(buyer);
        assertThat(emails).hasSize(1);
        assertThat(emails.get(0).getBody()).contains("£19.99").contains("time to pay had run out");
    }
}
