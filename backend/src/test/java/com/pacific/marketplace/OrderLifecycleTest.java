package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.SentEmail;
import com.pacific.marketplace.notify.Email;
import com.pacific.marketplace.notify.Mailer;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.SentEmailRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The order timeline, tracking and emails. Not transactional: emails are sent only once the order's transaction has
 * really committed, so these tests need real commits (and their own in-memory database). Every test uses fresh
 * accounts, so nothing needs cleaning up.
 */
class OrderLifecycleTest extends CommittedFlowTestBase {

    @MockitoSpyBean Mailer mailer;

    // ---------- placing orders ----------

    @Test
    void payOnDeliveryTellsTheCustomerAndTheSellerAndStartsTheTimeline() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Walnut Chopping Board", "25.00", 5);
        Account buyer = customer();
        addToCart(buyer, pid, 2);

        JsonNode res = checkout(buyer, null, 201);
        JsonNode order = res.get("orders").get(0);

        List<SentEmail> toBuyer = emailsTo(buyer.email());
        assertThat(toBuyer).hasSize(1);
        assertThat(toBuyer.get(0).getKind()).isEqualTo("ORDER_CONFIRMATION");
        assertThat(toBuyer.get(0).getSubject()).contains("#" + id(order)).contains("confirmed");
        assertThat(toBuyer.get(0).getBody()).contains("2 × Walnut Chopping Board").contains("Pay on delivery");
        assertThat(toBuyer.get(0).getStatus()).isEqualTo(SentEmail.Status.LOGGED); // no mail server in tests

        List<SentEmail> toSeller = emailsTo(seller.email());
        assertThat(toSeller).hasSize(1);
        assertThat(toSeller.get(0).getKind()).isEqualTo("SELLER_NEW_ORDER");
        assertThat(toSeller.get(0).getBody()).contains("2 × Walnut Chopping Board").contains("1 High Street")
                .contains("pay on delivery").doesNotContain(buyer.email()); // the seller needs the address, not the email

        JsonNode detail = order(buyer, id(order));
        assertThat(timeline(detail)).containsExactly("PLACED");
        assertThat(detail.get("timeline").get(0).get("note").asText()).isEqualTo("Pay on delivery");
    }

    @Test
    void oneCheckoutWithSeveralSellersIsOneConfirmationAndOneEmailPerSeller() throws Exception {
        Account a = seller();
        Account b = seller();
        long pa = listProduct(a, "Alpha Mug", "12.00", 5);
        long pb = listProduct(b, "Beta Plate", "9.00", 5);
        Account buyer = customer();
        addToCart(buyer, pa, 1);
        addToCart(buyer, pb, 1);

        JsonNode res = checkout(buyer, null, 201);
        assertThat(res.get("orders")).hasSize(2);

        List<SentEmail> toBuyer = emailsTo(buyer.email());
        assertThat(toBuyer).hasSize(1); // not one per order
        assertThat(toBuyer.get(0).getSubject()).contains("orders").contains("#" + id(res.get("orders").get(0)))
                .contains("#" + id(res.get("orders").get(1)));
        assertThat(toBuyer.get(0).getBody()).contains("Alpha Mug").contains("Beta Plate");
        assertThat(emailsTo(a.email())).hasSize(1);
        assertThat(emailsTo(b.email())).hasSize(1);
        assertThat(emailsTo(a.email()).get(0).getBody()).doesNotContain("Beta Plate"); // each seller sees only their own
    }

    @Test
    void aCardCheckoutIsOnlyAnnouncedOnceItIsPaid() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Linen Apron", "30.00", 5);
        Account buyer = customer();
        addToCart(buyer, pid, 1);

        JsonNode res = checkout(buyer, "CARD", 201);
        long orderId = id(res.get("orders").get(0));
        String ref = res.get("checkoutRef").asText();
        assertThat(emailsTo(buyer.email())).isEmpty(); // nothing yet: it isn't an order until it is paid
        assertThat(emailsTo(seller.email())).isEmpty();
        assertThat(timeline(order(buyer, orderId))).containsExactly("AWAITING_PAYMENT");

        send(post("/api/payments/" + ref + "/simulate"), buyer.token(), Map.of("outcome", "PAID"), 200);

        assertThat(emailsTo(buyer.email())).hasSize(1);
        assertThat(emailsTo(buyer.email()).get(0).getBody()).contains("Paid by card").contains("We've received your payment");
        assertThat(emailsTo(seller.email())).hasSize(1);
        assertThat(timeline(order(buyer, orderId))).containsExactly("AWAITING_PAYMENT", "PAYMENT_RECEIVED", "PLACED");

        send(post("/api/payments/" + ref + "/simulate"), buyer.token(), Map.of("outcome", "PAID"), 200); // a repeated webhook
        assertThat(emailsTo(buyer.email())).hasSize(1);
        assertThat(emailsTo(seller.email())).hasSize(1);
    }

    @Test
    void anAbandonedCardPaymentSendsNothingButShowsOnTheTimeline() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Cotton Throw", "40.00", 5);
        Account buyer = customer();
        addToCart(buyer, pid, 1);
        JsonNode res = checkout(buyer, "CARD", 201);

        send(post("/api/payments/" + res.get("checkoutRef").asText() + "/cancel"), buyer.token(), null, 200);

        assertThat(emailsTo(buyer.email())).isEmpty();
        assertThat(emailsTo(seller.email())).isEmpty();
        JsonNode detail = order(buyer, id(res.get("orders").get(0)));
        assertThat(timeline(detail)).containsExactly("AWAITING_PAYMENT", "CANCELLED");
        assertThat(detail.get("timeline").get(1).get("note").asText()).isEqualTo("Payment cancelled");
    }

    // ---------- shipping and delivery ----------

    @Test
    void shippingRecordsTheTrackingAndTellsTheCustomer() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Brass Candle Holder", "18.00", 5);
        Account buyer = customer();
        addToCart(buyer, pid, 1);
        long orderId = id(checkout(buyer, null, 201).get("orders").get(0));

        setStatus(seller, orderId, "PROCESSING", null, null);
        JsonNode shipped = setStatus(seller, orderId, "SHIPPED", "Royal Mail", "AB123456789GB");

        assertThat(shipped.get("trackingCarrier").asText()).isEqualTo("Royal Mail");
        assertThat(shipped.get("trackingNumber").asText()).isEqualTo("AB123456789GB");
        assertThat(shipped.get("trackingUrl").asText()).startsWith("https://www.royalmail.com/").endsWith("AB123456789GB");
        assertThat(shipped.get("shippedAt").isNull()).isFalse();
        assertThat(timeline(order(buyer, orderId))).containsExactly("PLACED", "PROCESSING", "SHIPPED");

        SentEmail email = emailsTo(buyer.email()).stream().filter(e -> e.getKind().equals("ORDER_SHIPPED")).findFirst().orElseThrow();
        assertThat(email.getSubject()).contains("has shipped");
        assertThat(email.getBody()).contains("AB123456789GB").contains("https://www.royalmail.com/");
    }

    @Test
    void aCourierWeDontKnowIsShownAsTextAndTrackingIsOptional() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Wool Blanket", "55.00", 5);
        Account buyer = customer();
        addToCart(buyer, pid, 1);
        long first = id(checkout(buyer, null, 201).get("orders").get(0));
        addToCart(buyer, pid, 1);
        long second = id(checkout(buyer, null, 201).get("orders").get(0));

        setStatus(seller, first, "PROCESSING", null, null);
        JsonNode local = setStatus(seller, first, "SHIPPED", "Bob's Vans", "VAN-42");
        assertThat(local.get("trackingNumber").asText()).isEqualTo("VAN-42");
        assertThat(local.get("trackingUrl").isNull()).isTrue(); // no link for a courier we don't know

        setStatus(seller, second, "PROCESSING", null, null);
        JsonNode byHand = setStatus(seller, second, "SHIPPED", null, null); // hand delivery: no tracking at all
        assertThat(byHand.get("trackingNumber").isNull()).isTrue();
        assertThat(byHand.get("status").asText()).isEqualTo("SHIPPED");
    }

    @Test
    void deliveryIsTimestampedAndTheCustomerIsToldWhereToReview() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Ceramic Vase", "22.00", 5);
        Account buyer = customer();
        addToCart(buyer, pid, 1);
        long orderId = id(checkout(buyer, null, 201).get("orders").get(0));
        setStatus(seller, orderId, "PROCESSING", null, null);
        setStatus(seller, orderId, "SHIPPED", null, null);

        JsonNode delivered = setStatus(seller, orderId, "DELIVERED", null, null);

        assertThat(delivered.get("deliveredAt").isNull()).isFalse();
        assertThat(timeline(order(buyer, orderId))).endsWith("DELIVERED");
        SentEmail email = emailsTo(buyer.email()).stream().filter(e -> e.getKind().equals("ORDER_DELIVERED")).findFirst().orElseThrow();
        assertThat(email.getBody()).contains("/account/reviews").contains("return");
    }

    // ---------- cancelling ----------

    @Test
    void cancellingTellsTheCustomerWhoDidItAndThatNothingWasCharged() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Glass Jug", "14.00", 5);
        Account buyer = customer();
        addToCart(buyer, pid, 1);
        long orderId = id(checkout(buyer, null, 201).get("orders").get(0));

        send(post("/api/orders/" + orderId + "/cancel"), buyer.token(), null, 200);

        JsonNode detail = order(buyer, orderId);
        assertThat(timeline(detail)).containsExactly("PLACED", "CANCELLED");
        assertThat(detail.get("timeline").get(1).get("note").asText()).isEqualTo("Cancelled by you");
        SentEmail email = emailsTo(buyer.email()).stream().filter(e -> e.getKind().equals("ORDER_CANCELLED")).findFirst().orElseThrow();
        assertThat(email.getBody()).contains("cancelled by you").contains("haven't been charged");
    }

    @Test
    void aSellerCancellingAPaidCardOrderRefundsItAndTheEmailSaysSo() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Oak Tray", "60.00", 5);
        Account buyer = customer();
        addToCart(buyer, pid, 1);
        JsonNode res = checkout(buyer, "CARD", 201);
        long orderId = id(res.get("orders").get(0));
        send(post("/api/payments/" + res.get("checkoutRef").asText() + "/simulate"), buyer.token(), Map.of("outcome", "PAID"), 200);

        setStatus(seller, orderId, "CANCELLED", null, null);

        JsonNode detail = order(buyer, orderId);
        assertThat(detail.get("timeline").get(detail.get("timeline").size() - 1).get("note").asText())
                .isEqualTo("Cancelled by the seller, refunded to the card");
        SentEmail email = emailsTo(buyer.email()).stream().filter(e -> e.getKind().equals("ORDER_CANCELLED")).findFirst().orElseThrow();
        assertThat(email.getBody()).contains("cancelled by the seller").contains("refunded to your card");
    }

    // ---------- reliability and safety ----------

    @Test
    void anOrderStillSucceedsWhenTheMailServerIsDown() throws Exception {
        doThrow(new IllegalStateException("smtp is down")).when(mailer).send(any(Email.class));
        Account seller = seller();
        long pid = listProduct(seller, "Tea Towel", "6.00", 5);
        Account buyer = customer();
        addToCart(buyer, pid, 1);

        JsonNode res = checkout(buyer, null, 201);

        assertThat(res.get("orders").get(0).get("status").asText()).isEqualTo("PLACED");
        List<SentEmail> toBuyer = emailsTo(buyer.email());
        assertThat(toBuyer).hasSize(1);
        assertThat(toBuyer.get(0).getStatus()).isEqualTo(SentEmail.Status.FAILED);
        assertThat(toBuyer.get(0).getError()).contains("smtp is down");
    }

    @Test
    void nobodyIsEmailedAboutACheckoutThatFailed() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Last One Left", "10.00", 5);
        Account buyer = customer();
        addToCart(buyer, pid, 3);
        Product p = products.findById(pid).orElseThrow();
        p.setStock(1); // someone else bought most of it meanwhile
        products.saveAndFlush(p);

        checkout(buyer, null, 409);

        assertThat(emailsTo(buyer.email())).isEmpty();
        assertThat(emailsTo(seller.email())).isEmpty();
    }

    @Test
    void whatPeopleTypeIsEscapedInTheHtmlVersionOfEmails() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Plain Bowl", "8.00", 5);
        Account buyer = customer();
        addToCart(buyer, pid, 1);

        checkout(buyer, null, "<script>alert(1)</script>", 201);

        ArgumentCaptor<Email> sent = ArgumentCaptor.forClass(Email.class);
        verify(mailer, org.mockito.Mockito.atLeastOnce()).send(sent.capture());
        Email toSeller = sent.getAllValues().stream().filter(e -> e.to().equals(seller.email())).findFirst().orElseThrow();
        assertThat(toSeller.html()).contains("&lt;script&gt;alert(1)&lt;/script&gt;").doesNotContain("<script>");
    }

    @Test
    void onlyAdminsCanReadTheEmailLog() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Soap Dish", "5.00", 5);
        Account buyer = customer();
        addToCart(buyer, pid, 1);
        checkout(buyer, null, 201);

        send(get("/api/admin/emails"), buyer.token(), null, 403);
        send(get("/api/admin/emails"), null, null, 401);
        JsonNode page = send(get("/api/admin/emails"), admin(), null, 200);
        assertThat(page.get("totalItems").asLong()).isGreaterThanOrEqualTo(2);
        JsonNode newest = page.get("items").get(0);
        assertThat(newest.has("to")).isTrue();
        assertThat(newest.get("subject").asText()).isNotBlank();
    }
}
