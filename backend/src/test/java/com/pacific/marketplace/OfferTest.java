package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Several sellers selling the same product: offers, the buy box, and one product page. */
class OfferTest extends CommittedFlowTestBase {

    private record Store(Account owner, long id) {
    }

    private Store store() throws Exception {
        Account owner = customer();
        JsonNode s = send(post("/api/seller/apply"), owner.token(),
                Map.of("storeName", "Offer Store " + UUID.randomUUID().toString().substring(0, 6), "description", "x"), 201);
        send(patch("/api/admin/sellers/" + s.get("id").asLong() + "/status"), admin(), Map.of("status", "APPROVED"), 200);
        return new Store(owner, s.get("id").asLong());
    }

    private long offer(Store s, long productId, String price, int stock, String condition, int expected) throws Exception {
        JsonNode r = send(post("/api/seller/offers/" + productId), s.owner().token(),
                Map.of("price", price, "stock", stock, "condition", condition), expected);
        return r == null || r.get("id") == null ? -1 : r.get("id").asLong();
    }

    private List<JsonNode> offers(long productId) throws Exception {
        List<JsonNode> out = new java.util.ArrayList<>();
        send(get("/api/products/" + productId + "/offers"), null, null, 200).forEach(out::add);
        return out;
    }

    /** The search results for a unique name: one card per product. */
    private JsonNode search(String name) throws Exception {
        return send(get("/api/products").param("q", name), null, null, 200).get("items");
    }

    @Test
    void aSecondSellerJoinsTheProductPageAndTheCheaperOfferWinsTheBuyBox() throws Exception {
        String name = "Buybox Kettle " + UUID.randomUUID().toString().substring(0, 6);
        Store a = store();
        Store b = store();
        long page = listProduct(a.owner(), name, "30.00", 5);
        long bOffer = offer(b, page, "27.50", 3, "NEW", 201);

        List<JsonNode> list = offers(page);
        assertThat(list).extracting(o -> o.get("productId").asLong()).containsExactly(bOffer, page);
        assertThat(list.get(0).get("buyBox").asBoolean()).isTrue();
        assertThat(list.get(1).get("buyBox").asBoolean()).isFalse();

        JsonNode cards = search(name);
        assertThat(cards).hasSize(1); // one card, not one per seller
        assertThat(cards.get(0).get("id").asLong()).isEqualTo(page);
        assertThat(cards.get(0).get("boxPrice").decimalValue()).isEqualByComparingTo("27.50");
        assertThat(cards.get(0).get("offerCount").asInt()).isEqualTo(2);
        assertThat(cards.get(0).get("boxProductId").asLong()).isEqualTo(bOffer); // "Add to cart" buys B's listing
        assertThat(cards.get(0).get("boxStock").asInt()).isEqualTo(3);

        // The offer's own address opens the product's page.
        assertThat(send(get("/api/products/" + bOffer), null, null, 200).get("id").asLong()).isEqualTo(page);
        // Each store's page lists its own listing.
        assertThat(offers(bOffer)).hasSize(2);
    }

    @Test
    void theBuyBoxMovesWhenTheWinnerSellsOutAndComesBackWhenStockReturns() throws Exception {
        String name = "Buybox Lamp " + UUID.randomUUID().toString().substring(0, 6);
        Store a = store();
        Store b = store();
        long page = listProduct(a.owner(), name, "40.00", 10);
        long bOffer = offer(b, page, "35.00", 1, "NEW", 201);

        Account buyer = customer();
        addToCart(buyer, bOffer, 1);
        JsonNode placed = checkout(buyer, null, 201);
        JsonNode order = placed.get("orders").get(0);
        assertThat(order.get("sellerName").asText()).isEqualTo(offers(page).stream()
                .filter(o -> o.get("productId").asLong() == bOffer).findFirst().orElseThrow().get("sellerName").asText());

        // B sold out: A's listing takes the buy box, and search shows A's price.
        assertThat(offers(page).get(0).get("productId").asLong()).isEqualTo(page);
        assertThat(search(name).get(0).get("boxPrice").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(search(name).get(0).get("boxProductId").asLong()).isEqualTo(page);

        send(post("/api/orders/" + id(order) + "/cancel"), buyer.token(), null, 200);
        assertThat(offers(page).get(0).get("productId").asLong()).isEqualTo(bOffer);
        assertThat(search(name).get(0).get("boxPrice").decimalValue()).isEqualByComparingTo("35.00");
    }

    @Test
    void newBeatsCheaperUsedUnlessNothingNewIsInStock() throws Exception {
        String name = "Buybox Camera " + UUID.randomUUID().toString().substring(0, 6);
        Store a = store();
        Store b = store();
        long page = listProduct(a.owner(), name, "200.00", 2);
        long used = offer(b, page, "120.00", 4, "USED_GOOD", 201);

        List<JsonNode> list = offers(page);
        assertThat(list.get(0).get("productId").asLong()).isEqualTo(page);
        assertThat(list.get(1).get("conditionLabel").asText()).isEqualTo("Used – good");

        send(patch("/api/seller/products/" + page + "/stock"), a.owner().token(), Map.of("stock", 0), 200);
        assertThat(offers(page).get(0).get("productId").asLong()).isEqualTo(used);
        assertThat(search(name).get(0).get("boxPrice").decimalValue()).isEqualByComparingTo("120.00");
    }

    @Test
    void theProductStaysOnSaleWhileAnySellerHasIt() throws Exception {
        String name = "Buybox Desk " + UUID.randomUUID().toString().substring(0, 6);
        Store a = store();
        Store b = store();
        long page = listProduct(a.owner(), name, "90.00", 3);
        long bOffer = offer(b, page, "95.00", 3, "NEW", 201);

        // A stops selling: the page stays, now with B's offer.
        send(delete("/api/seller/products/" + page), a.owner().token(), null, 200);
        assertThat(search(name)).hasSize(1);
        assertThat(offers(page)).extracting(o -> o.get("productId").asLong()).containsExactly(bOffer);
        send(get("/api/products/" + page), null, null, 200);

        // B is suspended too: nobody sells it any more.
        send(patch("/api/admin/sellers/" + b.id() + "/status"), admin(), Map.of("status", "SUSPENDED", "note", "x"), 200);
        assertThat(search(name)).isEmpty();
        send(get("/api/products/" + page), null, null, 404);
    }

    @Test
    void sellersCantDuplicateTheirOwnListingsAndMustBeApproved() throws Exception {
        Store a = store();
        Store b = store();
        long page = listProduct(a.owner(), "Buybox Rug", "50.00", 3);

        offer(a, page, "45.00", 1, "NEW", 409);              // already sells it
        long bOffer = offer(b, page, "45.00", 1, "NEW", 201);
        offer(b, page, "44.00", 1, "USED_GOOD", 409);        // one listing per seller
        offer(b, bOffer, "44.00", 1, "NEW", 409);            // via the offer's id too

        Account plain = customer();
        send(post("/api/seller/offers/" + page), plain.token(), Map.of("price", "40.00", "stock", 1, "condition", "NEW"), 403);
        send(post("/api/seller/offers/" + page), b.owner().token(), Map.of("price", "0", "stock", 1, "condition", "NEW"), 400);
        Store c = store();
        send(post("/api/seller/offers/" + page), c.owner().token(),
                Map.of("price", "40.00", "listPrice", "39.00", "stock", 1, "condition", "NEW"), 400);
        send(post("/api/seller/offers/999999"), c.owner().token(), Map.of("price", "40.00", "stock", 1, "condition", "NEW"), 404);
    }

    @Test
    void theCatalogPageOwnsTheDetailsAndReviewsFromAnySellersBuyers() throws Exception {
        String name = "Buybox Mug " + UUID.randomUUID().toString().substring(0, 6);
        Store a = store();
        Store b = store();
        long page = listProduct(a.owner(), name, "12.00", 5);
        long bOffer = offer(b, page, "10.00", 5, "NEW", 201);

        // B can change their price and condition, not the product's name.
        Map<String, Object> edit = new LinkedHashMap<>(Map.of("name", "Renamed by B", "price", "9.50", "stock", 5, "condition", "USED_LIKE_NEW"));
        JsonNode edited = send(put("/api/seller/products/" + bOffer), b.owner().token(), edit, 200);
        assertThat(edited.get("name").asText()).isEqualTo(name);
        assertThat(edited.get("condition").asText()).isEqualTo("USED_LIKE_NEW");

        // A renames the product: B's offer follows (it shows in carts and orders).
        Map<String, Object> rename = new LinkedHashMap<>(Map.of("name", name + " XL", "price", "12.00", "stock", 5));
        send(put("/api/seller/products/" + page), a.owner().token(), rename, 200);
        assertThat(send(get("/api/seller/products/" + bOffer), b.owner().token(), null, 200).get("name").asText()).isEqualTo(name + " XL");

        // Someone who bought from B reviews the product's page.
        Account buyer = customer();
        addToCart(buyer, bOffer, 1);
        checkout(buyer, null, 201);
        send(post("/api/products/" + page + "/reviews"), buyer.token(),
                Map.of("rating", 5, "comment", "Great mug, bought from the second store."), 201);
        JsonNode reviews = send(get("/api/products/" + page + "/reviews"), null, null, 200);
        assertThat(reviews.get("summary").get("count").asInt()).isEqualTo(1);
        assertThat(reviews.get("reviews").get(0).get("productId").asLong()).isEqualTo(page);
    }
}
