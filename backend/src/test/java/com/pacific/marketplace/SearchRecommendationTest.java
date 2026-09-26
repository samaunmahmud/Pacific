package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Word-by-word search with best matches first, suggestions, "Frequently bought together", related, buy it again. */
class SearchRecommendationTest extends CommittedFlowTestBase {

    /** A made-up word, so each test only finds its own products in the shared database. */
    private static String tag() {
        return "zq" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private long product(Account seller, String name, String description, String price) throws Exception {
        return send(post("/api/seller/products"), seller.token(),
                Map.of("name", name, "description", description, "price", price, "stock", 20), 201).get("id").asLong();
    }

    private List<String> names(JsonNode items) {
        List<String> out = new ArrayList<>();
        items.forEach(p -> out.add(p.get("name").asText()));
        return out;
    }

    private List<String> search(String q) throws Exception {
        return names(send(get("/api/products").param("q", q), null, null, 200).get("items"));
    }

    @Test
    void everyWordMustMatchSomewhereAndTheBestMatchesComeFirst() throws Exception {
        String t = tag();
        Account seller = seller();
        product(seller, "Reading " + t + " for the desk", "Adjustable arm, lamp head", "30.00");  // words in name + description
        product(seller, t + " Lamp for your desk", "Warm light", "30.00");                       // every word in the name
        product(seller, "Desk Lamp " + t, "Walnut base", "30.00");                              // the phrase in the name
        product(seller, "Desk fan " + t, "Quiet", "30.00");                                     // no "lamp" anywhere

        assertThat(search("desk lamp " + t)).containsExactly("Desk Lamp " + t, t + " Lamp for your desk", "Reading " + t + " for the desk");
        assertThat(search(t + " walnut")).containsExactly("Desk Lamp " + t);             // a word from the description
        assertThat(search(t + " zebra")).isEmpty();
        // an explicit sort still wins over best match
        assertThat(names(send(get("/api/products").param("q", t).param("sort", "name"), null, null, 200).get("items")))
                .containsExactly("Desk Lamp " + t, "Desk fan " + t, "Reading " + t + " for the desk", t + " Lamp for your desk");
    }

    @Test
    void suggestionsOfferProductsAndCategoriesAsYouType() throws Exception {
        String t = tag();
        Account seller = seller();
        product(seller, "Suggest Kettle " + t, "Boils", "20.00");
        String category = "Kitchenware " + t;
        send(post("/api/admin/categories"), admin(), Map.of("name", category), 201);

        JsonNode s = send(get("/api/search/suggest").param("q", t), null, null, 200);
        assertThat(names(s.get("products"))).containsExactly("Suggest Kettle " + t);
        assertThat(s.get("categories").get(0).get("name").asText()).isEqualTo(category);
        assertThat(send(get("/api/search/suggest").param("q", "z"), null, null, 200).get("products")).isEmpty();
    }

    @Test
    void frequentlyBoughtTogetherCountsWholeCheckoutsFromAnySeller() throws Exception {
        String t = tag();
        Account a = seller();
        Account b = seller();
        long kettle = product(a, "Kettle " + t, "x", "20.00");
        long teapot = product(b, "Teapot " + t, "x", "15.00");
        long mugs = product(a, "Mugs " + t, "x", "8.00");
        long cosy = product(b, "Cosy " + t, "x", "5.00");

        // Two shoppers buy the kettle with the teapot (from another seller: a separate order, same checkout);
        // one also takes mugs.
        for (int n = 0; n < 2; n++) {
            Account buyer = customer();
            addToCart(buyer, kettle, 1);
            addToCart(buyer, teapot, 1);
            if (n == 0) addToCart(buyer, mugs, 1);
            checkout(buyer, null, 201);
        }
        // Someone buys a tea cosy on its own: not "together" with the kettle.
        Account other = customer();
        addToCart(other, cosy, 1);
        checkout(other, null, 201);

        JsonNode r = send(get("/api/products/" + kettle + "/recommendations"), null, null, 200);
        assertThat(names(r.get("boughtTogether"))).containsExactly("Teapot " + t, "Mugs " + t);
        assertThat(names(send(get("/api/products/" + teapot + "/recommendations"), null, null, 200).get("boughtTogether")))
                .startsWith("Kettle " + t);
    }

    @Test
    void relatedProductsComeFromTheSameCategory() throws Exception {
        String t = tag();
        long category = send(post("/api/admin/categories"), admin(), Map.of("name", "Related " + t), 201).get("id").asLong();
        long page = send(post("/api/admin/products"), admin(), Map.of("name", "Lamp " + t, "price", "10.00", "stock", 5, "categoryId", category), 201).get("id").asLong();
        send(post("/api/admin/products"), admin(), Map.of("name", "Shade " + t, "price", "10.00", "stock", 5, "categoryId", category), 201);
        send(post("/api/admin/products"), admin(), Map.of("name", "Unrelated " + t, "price", "10.00", "stock", 5), 201);

        JsonNode r = send(get("/api/products/" + page + "/recommendations"), null, null, 200);
        assertThat(names(r.get("related"))).containsExactly("Shade " + t);
    }

    @Test
    void buyItAgainListsDeliveredPurchases() throws Exception {
        String t = tag();
        Account seller = seller();
        long soap = product(seller, "Soap " + t, "x", "4.00");
        long brush = product(seller, "Brush " + t, "x", "6.00");
        Account buyer = customer();
        addToCart(buyer, soap, 1);
        long delivered = id(checkout(buyer, null, 201).get("orders").get(0));
        for (String s : List.of("PROCESSING", "SHIPPED", "DELIVERED")) {
            send(patch("/api/seller/orders/" + delivered + "/status"), seller.token(), Map.of("status", s), 200);
        }
        addToCart(buyer, brush, 1);
        checkout(buyer, null, 201); // not delivered yet

        List<String> again = names(send(get("/api/me/buy-again"), buyer.token(), null, 200));
        assertThat(again).contains("Soap " + t).doesNotContain("Brush " + t);
    }
}
