package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacific.marketplace.domain.Payment;
import com.pacific.marketplace.domain.PaymentProviderType;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.payment.PaymentGateway;
import com.pacific.marketplace.payment.PaymentGateways;
import com.pacific.marketplace.repo.PaymentRepository;
import com.pacific.marketplace.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** How payments behave when the provider misbehaves or things happen on its side that we only learn about later. */
class PaymentGatewayTest extends PaymentTestBase {

    /** A provider whose answers the test controls. */
    static class FakeGateway implements PaymentGateway {
        boolean failCreate;
        boolean failStatus;
        boolean failExpire;
        boolean failRefund;
        State state = State.OPEN;
        final List<String> refunds = new ArrayList<>();
        final List<String> expired = new ArrayList<>();

        @Override
        public PaymentProviderType type() {
            return PaymentProviderType.SIMULATOR;
        }

        @Override
        public GatewaySession create(CheckoutSpec spec) {
            if (failCreate) throw new GatewayException("provider down", null);
            return new GatewaySession("fake_" + spec.ref(), "https://pay.example/" + spec.ref());
        }

        @Override
        public GatewayStatus status(String sessionId) {
            if (failStatus) throw new GatewayException("provider down", null);
            return new GatewayStatus(state, state == State.PAID ? "pi_fake" : null);
        }

        @Override
        public void expire(String sessionId) {
            if (failExpire) throw new GatewayException("provider down", null);
            expired.add(sessionId);
        }

        @Override
        public String refund(String paymentRef, BigDecimal amount, String currency, String idempotencyKey) {
            if (failRefund) throw new GatewayException("provider down", null);
            refunds.add(idempotencyKey + ":" + amount.toPlainString());
            return "re_fake";
        }
    }

    @MockitoBean PaymentGateways gateways;
    @Autowired PaymentRepository paymentRepo;
    @Autowired PaymentService paymentService;

    FakeGateway fake;

    @BeforeEach
    void useFakeProvider() {
        fake = new FakeGateway();
        when(gateways.active()).thenReturn(Optional.of(fake));
        when(gateways.forType(any())).thenReturn(Optional.of(fake));
    }

    private JsonNode checkoutOf(String token, Product p, int qty) throws Exception {
        addToCart(token, p.getId(), qty);
        return cardCheckout(token);
    }

    @Test
    void ifTheProviderIsDownTheCheckoutIsCancelledAndStockReleased() throws Exception {
        String token = registerCustomer();
        Product p = product("Printer", "80.00", 4);
        addToCart(token, p.getId(), 2);
        fake.failCreate = true;

        JsonNode error = send(post("/api/orders"), token, cardRequest(), 502);
        assertThat(error.get("message").asText()).contains("Nothing was charged");

        assertThat(stockOf(p)).isEqualTo(4);
        Payment row = paymentRepo.findAll().get(0);
        assertThat(row.getStatus().name()).isEqualTo("CANCELLED");
        assertAllOrders(token, "CANCELLED");
    }

    private Map<String, Object> cardRequest() {
        Map<String, Object> req = new LinkedHashMap<>(address());
        req.put("paymentMethod", "CARD");
        return req;
    }

    private void assertAllOrders(String token, String expectedStatus) throws Exception {
        JsonNode orders = send(get("/api/orders"), token, null, 200);
        assertThat(orders).isNotEmpty();
        orders.forEach(o -> assertThat(o.get("status").asText()).isEqualTo(expectedStatus));
    }

    @Test
    void theReturnPagePicksUpAPaymentWithoutAWebhook() throws Exception {
        String token = registerCustomer();
        Product p = product("Scanner", "70.00", 3);
        JsonNode res = checkoutOf(token, p, 1);
        String ref = res.get("checkoutRef").asText();
        assertThat(payment(token, ref, 200).get("status").asText()).isEqualTo("PENDING");

        fake.state = PaymentGateway.State.PAID; // paid on the provider's page; the webhook never arrived
        assertThat(payment(token, ref, 200).get("status").asText()).isEqualTo("PAID");
        assertThat(order(token, res.get("orders").get(0).get("id").asLong()).get("status").asText())
                .isEqualTo("PLACED");
    }

    @Test
    void theReturnPagePicksUpAnExpiredSession() throws Exception {
        String token = registerCustomer();
        Product p = product("Dock", "90.00", 3);
        JsonNode res = checkoutOf(token, p, 1);
        String ref = res.get("checkoutRef").asText();

        fake.state = PaymentGateway.State.EXPIRED;
        assertThat(payment(token, ref, 200).get("status").asText()).isEqualTo("EXPIRED");
        assertThat(stockOf(p)).isEqualTo(3);
    }

    @Test
    void cancellingClosesTheProvidersPage() throws Exception {
        String token = registerCustomer();
        Product p = product("Fan", "20.00", 3);
        JsonNode res = checkoutOf(token, p, 1);

        cancelPayment(token, res.get("checkoutRef").asText(), 200);
        assertThat(fake.expired).containsExactly("fake_" + res.get("checkoutRef").asText());
    }

    @Test
    void cancellingWhileTheCustomerHadJustPaidKeepsTheOrder() throws Exception {
        String token = registerCustomer();
        Product p = product("Heater", "55.00", 3);
        JsonNode res = checkoutOf(token, p, 1);
        String ref = res.get("checkoutRef").asText();

        fake.state = PaymentGateway.State.PAID; // they paid a moment before pressing cancel
        cancelPayment(token, ref, 409);

        assertThat(payment(token, ref, 200).get("status").asText()).isEqualTo("PAID");
        assertThat(order(token, res.get("orders").get(0).get("id").asLong()).get("status").asText())
                .isEqualTo("PLACED");
        assertThat(stockOf(p)).isEqualTo(2);
    }

    @Test
    void weNeverCancelWhenTheProviderCannotConfirmTheCustomerHasntPaid() throws Exception {
        String token = registerCustomer();
        Product p = product("Kettle", "35.00", 3);
        JsonNode res = checkoutOf(token, p, 1);
        String ref = res.get("checkoutRef").asText();

        fake.failStatus = true;
        cancelPayment(token, ref, 502);
        fake.failStatus = false;
        fake.failExpire = true;
        cancelPayment(token, ref, 502);

        fake.failExpire = false;
        assertThat(payment(token, ref, 200).get("status").asText()).isEqualTo("PENDING");
        assertThat(stockOf(p)).isEqualTo(2); // still reserved for them
    }

    @Test
    void anOverduePaymentThatWasPaidIsNotExpired() throws Exception {
        String token = registerCustomer();
        Product p = product("Toaster", "40.00", 3);
        JsonNode res = checkoutOf(token, p, 1);
        String ref = res.get("checkoutRef").asText();
        Payment row = paymentRepo.findByCheckoutRef(ref).orElseThrow();
        row.setExpiresAt(Instant.now().minusSeconds(60));
        paymentRepo.saveAndFlush(row);

        fake.state = PaymentGateway.State.PAID; // paid just before the deadline, webhook still in flight
        paymentService.expireOverdue();

        assertThat(payment(token, ref, 200).get("status").asText()).isEqualTo("PAID");
        assertThat(order(token, res.get("orders").get(0).get("id").asLong()).get("status").asText())
                .isEqualTo("PLACED");
    }

    @Test
    void anOverduePaymentWhoseProviderIsUnreachableIsRetriedNotDropped() throws Exception {
        String token = registerCustomer();
        Product p = product("Blender", "40.00", 3);
        JsonNode res = checkoutOf(token, p, 1);
        String ref = res.get("checkoutRef").asText();
        Payment row = paymentRepo.findByCheckoutRef(ref).orElseThrow();
        row.setExpiresAt(Instant.now().minusSeconds(60));
        paymentRepo.saveAndFlush(row);

        fake.failStatus = true;
        assertThat(paymentService.expireOverdue()).isZero();
        assertThat(paymentRepo.findByCheckoutRef(ref).orElseThrow().getStatus().name()).isEqualTo("PENDING");

        fake.failStatus = false;
        assertThat(paymentService.expireOverdue()).isEqualTo(1);
        assertThat(paymentRepo.findByCheckoutRef(ref).orElseThrow().getStatus().name()).isEqualTo("EXPIRED");
    }

    @Test
    void refundsUseAStableKeySoARetryCannotRefundTwice() throws Exception {
        String token = registerCustomer();
        Product p = product("Iron", "25.00", 3);
        JsonNode res = checkoutOf(token, p, 1);
        String ref = res.get("checkoutRef").asText();
        long orderId = res.get("orders").get(0).get("id").asLong();
        fake.state = PaymentGateway.State.PAID;
        payment(token, ref, 200);

        send(post("/api/orders/" + orderId + "/cancel"), token, null, 200);
        assertThat(fake.refunds).containsExactly("pacific-refund-order-" + orderId + ":"
                + res.get("total").decimalValue().toPlainString());
    }

    @Test
    void aFailedRefundIsReportedAsAProviderProblem() throws Exception {
        String token = registerCustomer();
        Product p = product("Clock", "25.00", 3);
        JsonNode res = checkoutOf(token, p, 1);
        fake.state = PaymentGateway.State.PAID;
        payment(token, res.get("checkoutRef").asText(), 200);

        fake.failRefund = true;
        send(post("/api/orders/" + res.get("orders").get(0).get("id").asLong() + "/cancel"), token, null, 502);
    }
}
