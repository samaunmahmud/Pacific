package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacific.marketplace.domain.SentEmail;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Private messages between buyers and stores. Committed (not rolled back): message emails go out after commit. */
class MessagingTest extends CommittedFlowTestBase {

    private record Store(Account owner, long id, String slug) {
    }

    private Store store() throws Exception {
        Account owner = customer();
        JsonNode s = send(post("/api/seller/apply"), owner.token(),
                Map.of("storeName", "Chatty " + UUID.randomUUID().toString().substring(0, 6), "description", "Talks"), 201);
        send(patch("/api/admin/sellers/" + s.get("id").asLong() + "/status"), admin(), Map.of("status", "APPROVED"), 200);
        return new Store(owner, s.get("id").asLong(), s.get("slug").asText());
    }

    private JsonNode write(Account buyer, Store store, Long productId, Long orderId, String body, int expected) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("sellerSlug", store.slug());
        if (productId != null) req.put("productId", productId);
        if (orderId != null) req.put("orderId", orderId);
        req.put("body", body);
        return send(post("/api/messages"), buyer.token(), req, expected);
    }

    private List<SentEmail> messageEmailsTo(Account a) {
        return emailsTo(a.email()).stream().filter(e -> e.getKind().equals("NEW_MESSAGE")).toList();
    }

    @Test
    void aBuyerAndAStoreTalkWithUnreadCountsAndOneEmailPerUnreadStretch() throws Exception {
        Store store = store();
        long lamp = listProduct(store.owner(), "Chatty lamp", "20.00", 5);
        Account bea = customer();

        JsonNode c = write(bea, store, lamp, null, "Does the lamp come with a bulb?", 201);
        long id = c.get("id").asLong();
        assertThat(c.get("side").asText()).isEqualTo("BUYER");
        JsonNode first = c.get("messages").get(0);
        assertThat(first.get("mine").asBoolean()).isTrue();
        assertThat(first.get("product").get("name").asText()).isEqualTo("Chatty lamp");

        // A second message continues the same conversation, and doesn't email the seller again.
        assertThat(write(bea, store, null, null, "And is it dimmable?", 201).get("id").asLong()).isEqualTo(id);
        List<SentEmail> toSeller = messageEmailsTo(store.owner());
        assertThat(toSeller).hasSize(1);
        assertThat(toSeller.get(0).getBody()).contains("/seller/messages/" + id).doesNotContain("bulb"); // never quoted

        JsonNode inbox = send(get("/api/seller/messages"), store.owner().token(), null, 200);
        assertThat(inbox.get(0).get("with").asText()).isEqualTo("Casey Customer");
        assertThat(inbox.get(0).get("unread").asInt()).isEqualTo(2);
        assertThat(inbox.get(0).get("preview").asText()).isEqualTo("And is it dimmable?");
        assertThat(send(get("/api/messages/unread"), store.owner().token(), null, 200).get("asSeller").asLong()).isEqualTo(1);

        JsonNode opened = send(get("/api/messages/" + id), store.owner().token(), null, 200);
        assertThat(opened.get("side").asText()).isEqualTo("SELLER");
        assertThat(opened.get("messages")).hasSize(2);
        assertThat(opened.get("messages").get(0).get("mine").asBoolean()).isFalse();
        assertThat(send(get("/api/messages/unread"), store.owner().token(), null, 200).get("asSeller").asLong()).isZero();

        send(post("/api/messages/" + id), store.owner().token(), Map.of("body", "Yes, and yes!"), 201);
        assertThat(messageEmailsTo(bea)).hasSize(1);
        JsonNode beaInbox = send(get("/api/messages"), bea.token(), null, 200);
        assertThat(beaInbox.get(0).get("unread").asInt()).isEqualTo(1);
        assertThat(beaInbox.get(0).get("with").asText()).isEqualTo(c.get("storeName").asText()).startsWith("Chatty ");
        JsonNode beaView = send(get("/api/messages/" + id), bea.token(), null, 200);
        assertThat(beaView.get("messages").get(2).get("senderName").asText()).isEqualTo(beaView.get("storeName").asText());
        assertThat(send(get("/api/messages/unread"), bea.token(), null, 200).get("asBuyer").asLong()).isZero();

        // Once the seller has read everything, the next message emails them again.
        send(post("/api/messages/" + id), bea.token(), Map.of("body", "Thanks, ordering now."), 201);
        assertThat(messageEmailsTo(store.owner())).hasSize(2);
    }

    @Test
    void onlyTheTwoSidesCanReadOrReply() throws Exception {
        Store store = store();
        Account bea = customer();
        long id = write(bea, store, null, null, "Hello", 201).get("id").asLong();
        Account nosy = customer();

        send(get("/api/messages/" + id), nosy.token(), null, 404);
        send(post("/api/messages/" + id), nosy.token(), Map.of("body", "Hi"), 404);
        send(get("/api/messages/" + id), admin(), null, 403);
        send(get("/api/messages/" + id), null, null, 401);
        assertThat(send(get("/api/messages"), nosy.token(), null, 200)).isEmpty();
    }

    @Test
    void messagesMustBeAboutThisStoresProductsAndTheBuyersOwnOrders() throws Exception {
        Store store = store();
        Store other = store();
        long othersProduct = listProduct(other.owner(), "Someone else's mug", "4.00", 5);
        long ownProduct = listProduct(store.owner(), "Chatty mug", "4.00", 5);
        Account bea = customer();
        Account sam = customer();
        addToCart(sam, ownProduct, 1);
        long samsOrder = id(checkout(sam, null, 201).get("orders").get(0));

        write(bea, store, othersProduct, null, "Hi", 400);
        write(bea, store, null, samsOrder, "Hi", 404);            // not her order
        write(sam, other, null, samsOrder, "Hi", 404);            // his order, but not from that store
        write(sam, store, ownProduct, samsOrder, "Where is it?", 201);
        write(store.owner(), store, null, null, "Talking to myself", 400);
        send(post("/api/messages"), bea.token(), Map.of("sellerSlug", "no-such-store", "body", "Hi"), 404);
        write(bea, store, null, null, "   ", 400);
        write(bea, store, null, null, "x".repeat(2001), 400);
    }

    @Test
    void aSellerCanWriteToTheBuyerOfTheirOrderOnly() throws Exception {
        Store store = store();
        Store other = store();
        long product = listProduct(store.owner(), "Chatty vase", "12.00", 5);
        Account bea = customer();
        addToCart(bea, product, 1);
        long order = id(checkout(bea, null, 201).get("orders").get(0));

        send(post("/api/seller/messages"), other.owner().token(), Map.of("orderId", order, "body", "Hi"), 404);
        JsonNode c = send(post("/api/seller/messages"), store.owner().token(), Map.of("orderId", order, "body", "Your vase ships tomorrow."), 201);
        assertThat(c.get("side").asText()).isEqualTo("SELLER");
        assertThat(c.get("messages").get(0).get("orderId").asLong()).isEqualTo(order);

        JsonNode inbox = send(get("/api/messages"), bea.token(), null, 200);
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).get("id").asLong()).isEqualTo(c.get("id").asLong());
        assertThat(messageEmailsTo(bea)).hasSize(1);
    }

    @Test
    void aSuspendedStoreCantBeMessagedAndTheThreadSaysSo() throws Exception {
        Store store = store();
        Account bea = customer();
        long id = write(bea, store, null, null, "Hello", 201).get("id").asLong();
        send(patch("/api/admin/sellers/" + store.id() + "/status"), admin(), Map.of("status", "SUSPENDED", "note", "Checks"), 200);

        write(bea, store, null, null, "Anyone there?", 404);
        send(post("/api/messages/" + id), bea.token(), Map.of("body", "Anyone there?"), 403);
        JsonNode view = send(get("/api/messages/" + id), bea.token(), null, 200);
        assertThat(view.get("canReply").asBoolean()).isFalse();
        assertThat(view.get("messages")).hasSize(1); // the history stays readable
    }

    @Test
    void customersMustConfirmTheirEmailBeforeMessaging() throws Exception {
        Store store = store();
        String email = "u-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String token = send(post("/api/auth/register"), null,
                Map.of("name", "Una Confirmed", "email", email, "password", "correct-horse-battery"), 201).get("token").asText();
        JsonNode refused = write(new Account(email, token), store, null, null, "Hello?", 403);
        assertThat(refused.get("message").asText()).contains("confirm your email");
    }
}
