package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Choosing standard or express per seller, sellers' own free-delivery amounts, delivery dates, save for later. */
class DeliveryFlowTest extends CommittedFlowTestBase {

    private record Store(Account owner, String slug) {
    }

    private Store store() throws Exception {
        Account owner = customer();
        JsonNode s = send(post("/api/seller/apply"), owner.token(),
                Map.of("storeName", "Ship Store " + UUID.randomUUID().toString().substring(0, 6), "description", "x"), 201);
        send(patch("/api/admin/sellers/" + s.get("id").asLong() + "/status"), admin(), Map.of("status", "APPROVED"), 200);
        return new Store(owner, s.get("slug").asText());
    }

    private JsonNode cart(Account c) throws Exception {
        return send(get("/api/cart"), c.token(), null, 200);
    }

    private JsonNode shipment(JsonNode cart, String key) {
        for (JsonNode s : cart.get("shipments")) if (s.get("key").asText().equals(key)) return s;
        throw new AssertionError("no shipment " + key + " in " + cart);
    }

    @Test
    void eachShipmentOffersStandardAndExpressWithDates() throws Exception {
        Store store = store();
        long product = listProduct(store.owner(), "Ship kettle", "20.00", 5);
        Account buyer = customer();
        addToCart(buyer, product, 1);

        JsonNode s = shipment(cart(buyer), store.slug());
        assertThat(s.get("shipping").decimalValue()).isEqualByComparingTo("3.99");
        assertThat(s.get("toFreeDelivery").decimalValue()).isEqualByComparingTo("30.00");
        JsonNode standard = s.get("choices").get(0);
        JsonNode express = s.get("choices").get(1);
        assertThat(standard.get("option").asText()).isEqualTo("STANDARD");
        assertThat(express.get("fee").decimalValue()).isEqualByComparingTo("5.99");
        // express arrives before standard does
        assertThat(express.get("from").asText()).isLessThan(standard.get("from").asText());
    }

    @Test
    void aSellersOwnFreeDeliveryAmountAndDispatchTimeApply() throws Exception {
        Store store = store();
        long product = listProduct(store.owner(), "Ship lamp", "25.00", 5);
        Account buyer = customer();
        addToCart(buyer, product, 1);
        String before = shipment(cart(buyer), store.slug()).get("choices").get(0).get("from").asText();

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("freeDeliveryThreshold", "20.00");
        settings.put("dispatchDays", 3);
        JsonNode me = send(put("/api/seller/me/delivery"), store.owner().token(), settings, 200);
        assertThat(me.get("dispatchDays").asInt()).isEqualTo(3);

        JsonNode s = shipment(cart(buyer), store.slug());
        assertThat(s.get("shipping").decimalValue()).isEqualByComparingTo("0");
        assertThat(s.get("choices").get(0).get("from").asText()).isGreaterThan(before); // slower dispatch, later arrival

        send(put("/api/seller/me/delivery"), store.owner().token(), Map.of("dispatchDays", 6), 400);
        send(put("/api/seller/me/delivery"), buyer.token(), Map.of("dispatchDays", 1), 403);
    }

    @Test
    void checkoutChargesAndRecordsTheChosenDeliveryPerSeller() throws Exception {
        Store fast = store();
        Store slow = store();
        long a = listProduct(fast.owner(), "Ship mug", "10.00", 5);
        long b = listProduct(slow.owner(), "Ship bowl", "60.00", 5);
        Account buyer = customer();
        addToCart(buyer, a, 1);
        addToCart(buyer, b, 1);

        Map<String, Object> req = new LinkedHashMap<>(Map.of("name", "Casey", "line1", "1 High St", "city", "Uxbridge",
                "postcode", "UB8 1AA", "country", "United Kingdom"));
        req.put("delivery", Map.of(fast.slug(), "EXPRESS"));
        JsonNode placed = send(post("/api/orders"), buyer.token(), req, 201);

        JsonNode express = null, standard = null;
        for (JsonNode o : placed.get("orders")) {
            if (o.get("sellerSlug").asText().equals(fast.slug())) express = o;
            else standard = o;
        }
        assertThat(express.get("deliveryOption").asText()).isEqualTo("EXPRESS");
        assertThat(express.get("shipping").decimalValue()).isEqualByComparingTo("5.99");
        assertThat(express.get("deliveryFrom").asText()).isEqualTo(express.get("deliveryTo").asText());
        assertThat(standard.get("deliveryOption").asText()).isEqualTo("STANDARD");
        assertThat(standard.get("shipping").decimalValue()).isEqualByComparingTo("0"); // £60 is over the free amount
        assertThat(standard.get("deliveryFrom").isNull()).isFalse();

        String email = emailsTo(buyer.email()).get(0).getBody();
        assertThat(email).contains("Express delivery: arriving").contains("Standard delivery: arriving");
    }

    @Test
    void savedForLaterItemsStayOutOfTotalsAndCheckout() throws Exception {
        Store store = store();
        long keep = listProduct(store.owner(), "Ship vase", "30.00", 5);
        long later = listProduct(store.owner(), "Ship rug", "80.00", 5);
        Account buyer = customer();
        addToCart(buyer, keep, 1);
        addToCart(buyer, later, 1);

        JsonNode cart = send(post("/api/cart/items/" + later + "/save-for-later"), buyer.token(), null, 200);
        assertThat(cart.get("items")).hasSize(1);
        assertThat(cart.get("saved")).hasSize(1);
        assertThat(cart.get("subtotal").decimalValue()).isEqualByComparingTo("30.00");

        JsonNode placed = checkout(buyer, null, 201);
        assertThat(placed.get("orders").get(0).get("items")).hasSize(1);
        JsonNode after = cart(buyer);
        assertThat(after.get("items")).isEmpty();
        assertThat(after.get("saved").get(0).get("productId").asLong()).isEqualTo(later); // still saved

        JsonNode moved = send(post("/api/cart/items/" + later + "/move-to-cart"), buyer.token(), null, 200);
        assertThat(moved.get("items")).hasSize(1);
        assertThat(moved.get("saved")).isEmpty();
    }

    @Test
    void productPagesPromiseDeliveryPerOffer() throws Exception {
        Store store = store();
        long product = listProduct(store.owner(), "Ship clock", "15.00", 5);
        JsonNode offer = send(get("/api/products/" + product + "/offers"), null, null, 200).get(0);
        assertThat(offer.get("standardFee").decimalValue()).isEqualByComparingTo("3.99");
        assertThat(offer.get("freeDeliveryFrom").decimalValue()).isEqualByComparingTo("50");
        assertThat(offer.get("expressFee").decimalValue()).isEqualByComparingTo("5.99");
        assertThat(offer.get("expressDate").asText()).isLessThan(offer.get("standardFrom").asText());
    }
}
