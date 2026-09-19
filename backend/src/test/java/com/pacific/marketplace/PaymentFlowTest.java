package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacific.marketplace.domain.Payment;
import com.pacific.marketplace.domain.PaymentProviderType;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.repo.PaymentRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Card checkout against the test-mode simulator (the test profile enables it). */
class PaymentFlowTest extends PaymentTestBase {

    @Autowired PaymentRepository paymentRepo;
    @Autowired UserRepository userRepo;
    @Autowired PaymentService paymentService;

    @Test
    void cardCheckoutReservesStockAndWaitsForPayment() throws Exception {
        String token = registerCustomer();
        Product p = product("Headphones", "30.00", 5);
        addToCart(token, p.getId(), 2);

        JsonNode res = cardCheckout(token);
        JsonNode order = res.get("orders").get(0);
        JsonNode payment = res.get("payment");

        assertThat(order.get("status").asText()).isEqualTo("AWAITING_PAYMENT");
        assertThat(order.get("paymentMethod").asText()).isEqualTo("CARD");
        assertThat(order.get("cancellableByCustomer").asBoolean()).isFalse();
        assertThat(payment.get("status").asText()).isEqualTo("PENDING");
        assertThat(payment.get("provider").asText()).isEqualTo("SIMULATOR");
        assertThat(payment.get("simulator").asBoolean()).isTrue();
        assertThat(payment.get("amount").decimalValue()).isEqualByComparingTo(res.get("total").decimalValue());
        assertThat(payment.get("checkoutUrl").asText()).endsWith("/pay/simulate/" + res.get("checkoutRef").asText());
        assertThat(stockOf(p)).isEqualTo(3); // reserved
        mvc.perform(bearer(get("/api/cart"), token)).andExpect(jsonPath("$.itemCount").value(0));
        // the customer can see it and is told it awaits payment
        mvc.perform(bearer(get("/api/orders"), token)).andExpect(jsonPath("$[0].status").value("AWAITING_PAYMENT"));
        // it isn't cancellable as an ordinary order: the payment is what ends it
        send(post("/api/orders/" + order.get("id").asLong() + "/cancel"), token, null, 409);
    }

    @Test
    void payingPlacesTheOrdersAndRepeatingItChangesNothing() throws Exception {
        String token = registerCustomer();
        Product p = product("Keyboard", "40.00", 5);
        addToCart(token, p.getId(), 1);
        JsonNode res = cardCheckout(token);
        String ref = res.get("checkoutRef").asText();
        long orderId = res.get("orders").get(0).get("id").asLong();

        JsonNode paid = simulate(token, ref, "PAID", 200);
        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(paid.get("checkoutUrl").isNull()).isTrue(); // nothing left to pay
        assertThat(order(token, orderId).get("status").asText()).isEqualTo("PLACED");
        assertThat(order(token, orderId).get("cancellableByCustomer").asBoolean()).isTrue();

        simulate(token, ref, "PAID", 200); // a repeated webhook / double click
        assertThat(payment(token, ref, 200).get("status").asText()).isEqualTo("PAID");
        assertThat(payment(token, ref, 200).get("refundedAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(order(token, orderId).get("status").asText()).isEqualTo("PLACED");
        assertThat(stockOf(p)).isEqualTo(4); // still just the one unit
        // paid orders can no longer be abandoned through the payment
        cancelPayment(token, ref, 409);
    }

    @Test
    void sellersOnlySeeCardOrdersOnceTheyArePaid() throws Exception {
        Seller seller = approvedSeller("Card Store");
        Product p = sellerProduct(seller, "Lamp", "25.00", 4);
        String buyer = registerCustomer();
        addToCart(buyer, p.getId(), 1);
        JsonNode res = cardCheckout(buyer);

        mvc.perform(bearer(get("/api/seller/orders"), seller.token())).andExpect(jsonPath("$.totalItems").value(0));
        simulate(buyer, res.get("checkoutRef").asText(), "PAID", 200);
        mvc.perform(bearer(get("/api/seller/orders"), seller.token())).andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void oneCheckoutWithSeveralSellersIsOnePaymentForTheGrandTotal() throws Exception {
        Seller a = approvedSeller("Alpha Cards");
        Seller b = approvedSeller("Beta Cards");
        Product pa = sellerProduct(a, "Alpha Thing", "30.00", 3);
        Product pb = sellerProduct(b, "Beta Thing", "20.00", 3);
        String buyer = registerCustomer();
        addToCart(buyer, pa.getId(), 1);
        addToCart(buyer, pb.getId(), 1);

        JsonNode res = cardCheckout(buyer);
        String ref = res.get("checkoutRef").asText();
        assertThat(res.get("orders")).hasSize(2);
        // 30.00 + 3.99 shipping, 20.00 + 3.99 shipping (each seller ships separately)
        assertThat(res.get("payment").get("amount").decimalValue()).isEqualByComparingTo("57.98");

        simulate(buyer, ref, "PAID", 200);
        for (JsonNode o : res.get("orders")) {
            assertThat(order(buyer, o.get("id").asLong()).get("status").asText()).isEqualTo("PLACED");
        }

        // cancelling just one of them refunds just that order's total
        JsonNode first = res.get("orders").get(0);
        send(post("/api/orders/" + first.get("id").asLong() + "/cancel"), buyer, null, 200);
        JsonNode after = payment(buyer, ref, 200);
        assertThat(after.get("refundedAmount").decimalValue()).isEqualByComparingTo(first.get("total").decimalValue());
        assertThat(after.get("status").asText()).isEqualTo("PAID");
    }

    @Test
    void cancellingAPendingPaymentReleasesTheStock() throws Exception {
        String token = registerCustomer();
        Product p = product("Webcam", "50.00", 3);
        addToCart(token, p.getId(), 2);
        JsonNode res = cardCheckout(token);
        String ref = res.get("checkoutRef").asText();
        long orderId = res.get("orders").get(0).get("id").asLong();
        assertThat(stockOf(p)).isEqualTo(1);

        assertThat(cancelPayment(token, ref, 200).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(order(token, orderId).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(stockOf(p)).isEqualTo(3);

        cancelPayment(token, ref, 200); // cancelling twice must not restock twice
        assertThat(stockOf(p)).isEqualTo(3);
    }

    @Test
    void aPaymentThatArrivesAfterCancellationIsRefundedNotKept() throws Exception {
        String token = registerCustomer();
        Product p = product("Router", "60.00", 3);
        addToCart(token, p.getId(), 1);
        JsonNode res = cardCheckout(token);
        String ref = res.get("checkoutRef").asText();
        cancelPayment(token, ref, 200);

        simulate(token, ref, "PAID", 200); // the customer paid on the provider's page at the last moment

        JsonNode after = payment(token, ref, 200);
        assertThat(after.get("refundedAmount").decimalValue()).isEqualByComparingTo(after.get("amount").decimalValue());
        assertThat(order(token, res.get("orders").get(0).get("id").asLong()).get("status").asText())
                .isEqualTo("CANCELLED"); // no goods are reserved for it
        assertThat(stockOf(p)).isEqualTo(3); // and the stock was not taken twice or lost
    }

    @Test
    void unpaidPaymentsExpireAndReleaseTheirStock() throws Exception {
        String token = registerCustomer();
        Product p = product("Monitor", "100.00", 2);
        addToCart(token, p.getId(), 2);
        JsonNode res = cardCheckout(token);
        String ref = res.get("checkoutRef").asText();
        assertThat(stockOf(p)).isEqualTo(0);

        assertThat(paymentService.expireOverdue()).isZero(); // not due yet
        Payment row = paymentRepo.findByCheckoutRef(ref).orElseThrow();
        row.setExpiresAt(Instant.now().minusSeconds(60));
        paymentRepo.saveAndFlush(row);

        assertThat(paymentService.expireOverdue()).isEqualTo(1);
        assertThat(payment(token, ref, 200).get("status").asText()).isEqualTo("EXPIRED");
        assertThat(order(token, res.get("orders").get(0).get("id").asLong()).get("status").asText())
                .isEqualTo("CANCELLED");
        assertThat(stockOf(p)).isEqualTo(2);
        assertThat(paymentService.expireOverdue()).isZero(); // and it doesn't run again
        assertThat(stockOf(p)).isEqualTo(2);
    }

    @Test
    void cancellingAPaidCardOrderRefundsItOnlyOnce() throws Exception {
        String token = registerCustomer();
        Product p = product("Speaker", "45.00", 3);
        addToCart(token, p.getId(), 1);
        JsonNode res = cardCheckout(token);
        String ref = res.get("checkoutRef").asText();
        long orderId = res.get("orders").get(0).get("id").asLong();
        simulate(token, ref, "PAID", 200);

        send(post("/api/orders/" + orderId + "/cancel"), token, null, 200);
        JsonNode after = payment(token, ref, 200);
        assertThat(after.get("refundedAmount").decimalValue()).isEqualByComparingTo(res.get("total").decimalValue());
        assertThat(stockOf(p)).isEqualTo(3);

        send(post("/api/orders/" + orderId + "/cancel"), token, null, 409); // already cancelled
        assertThat(payment(token, ref, 200).get("refundedAmount").decimalValue())
                .isEqualByComparingTo(res.get("total").decimalValue());
    }

    @Test
    void anAdminCancellingAPaidCardOrderRefundsIt() throws Exception {
        String token = registerCustomer();
        Product p = product("Cable", "10.00", 5);
        addToCart(token, p.getId(), 1);
        JsonNode res = cardCheckout(token);
        simulate(token, res.get("checkoutRef").asText(), "PAID", 200);

        send(patch("/api/admin/orders/" + res.get("orders").get(0).get("id").asLong() + "/status"), adminToken(),
                Map.of("status", "CANCELLED"), 200);
        assertThat(payment(token, res.get("checkoutRef").asText(), 200).get("refundedAmount").decimalValue())
                .isEqualByComparingTo(res.get("total").decimalValue());
    }

    @Test
    void payOnDeliveryIsUnchanged() throws Exception {
        String token = registerCustomer();
        Product p = product("Mouse", "15.00", 5);
        addToCart(token, p.getId(), 1);
        JsonNode res = send(post("/api/orders"), token, address(), 201);

        assertThat(res.get("payment").isNull()).isTrue();
        assertThat(res.get("orders").get(0).get("status").asText()).isEqualTo("PLACED");
        assertThat(res.get("orders").get(0).get("paymentMethod").asText()).isEqualTo("PAY_ON_DELIVERY");
        assertThat(paymentRepo.count()).isZero();
    }

    @Test
    void paymentsBelongToTheirCustomer() throws Exception {
        String owner = registerCustomer();
        String other = registerCustomer();
        Product p = product("Tablet", "200.00", 2);
        addToCart(owner, p.getId(), 1);
        String ref = cardCheckout(owner).get("checkoutRef").asText();

        payment(other, ref, 404);
        cancelPayment(other, ref, 404);
        simulate(other, ref, "PAID", 404);
        payment(owner, ref, 200);
        mvc.perform(get("/api/payments/" + ref)).andExpect(status().isUnauthorized());
    }

    @Test
    void theSimulatorCannotBeUsedOnARealProvidersPayment() throws Exception {
        String email = uniqueEmail();
        String token = registerCustomer(email);
        // a payment that Stripe (not the simulator) is handling, owned by this customer
        String ref = "11111111-1111-1111-1111-111111111111";
        paymentRepo.saveAndFlush(new Payment(ref, userRepo.findByEmailIgnoreCase(email).orElseThrow(),
                PaymentProviderType.STRIPE, "cs_test_1", "https://checkout.stripe.com/c/pay/cs_test_1",
                new BigDecimal("10.00"), "GBP", Instant.now().plusSeconds(3600)));

        simulate(token, ref, "PAID", 404); // even the owner can't fake a real payment
        assertThat(paymentRepo.findByCheckoutRef(ref).orElseThrow().getStatus().name()).isEqualTo("PENDING");
    }

    @Test
    void theWebhookIsDisabledWithoutASecret() throws Exception {
        mvc.perform(post("/api/payments/webhook").contentType("application/json").content("{}")
                .header("Stripe-Signature", "t=1,v1=abc")).andExpect(status().isNotFound());
    }

    @Test
    void paymentConfigTellsTheStorefrontWhatIsAvailable() throws Exception {
        String token = registerCustomer();
        mvc.perform(bearer(get("/api/payments/config"), token))
                .andExpect(jsonPath("$.cardEnabled").value(true))
                .andExpect(jsonPath("$.simulator").value(true));
    }
}
