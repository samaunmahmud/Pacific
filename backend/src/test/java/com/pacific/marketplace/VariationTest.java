package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Variations: one product in several colours and sizes, one card in search, a picker on the page. */
class VariationTest extends CommittedFlowTestBase {

    private static String unique(String name) {
        return name + " " + UUID.randomUUID().toString().substring(0, 6);
    }

    private JsonNode family(Account seller, long productId, String dim1, String dim2, String o1, String o2, int expected)
            throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("dim1", dim1, "option1", o1));
        if (dim2 != null) body.put("dim2", dim2);
        if (o2 != null) body.put("option2", o2);
        return send(put("/api/seller/products/" + productId + "/family"), seller.token(), body, expected);
    }

    private long variation(Account seller, long productId, String o1, String o2, String price, int stock, int expected)
            throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("option1", o1, "price", price, "stock", stock));
        if (o2 != null) body.put("option2", o2);
        JsonNode r = send(post("/api/seller/products/" + productId + "/variations"), seller.token(), body, expected);
        return r == null || r.get("id") == null ? -1 : r.get("id").asLong();
    }

    private JsonNode search(String q) throws Exception {
        return send(get("/api/products").param("q", q), null, null, 200).get("items");
    }

    private static List<String> options(JsonNode page) {
        List<String> out = new ArrayList<>();
        page.get("variations").get("options").forEach(o -> out.add(o.get("option1").asText()
                + (o.get("option2").isNull() ? "" : "/" + o.get("option2").asText())));
        return out;
    }

    @Test
    void variationsShowAsOneCardWithAPickerAndEachIsBoughtOnItsOwn() throws Exception {
        String name = unique("Variation Tee");
        Account seller = seller();
        long red = listProduct(seller, name, "12.00", 5);
        JsonNode set = family(seller, red, "Colour", "Size", "Red", "M", 200);
        assertThat(set.get("variation").asText()).isEqualTo("Colour: Red, Size: M");
        long redL = variation(seller, red, "Red", "L", "13.00", 4, 201);
        long blue = variation(seller, red, "Blue", "M", "12.00", 0, 201); // sold out, still on the picker

        // Search: one card for the family, saying how many variations it has.
        JsonNode cards = search(name);
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).get("id").asLong()).isEqualTo(red);
        assertThat(cards.get(0).get("variationCount").asInt()).isEqualTo(3);
        // Words that only name another variation still find the family.
        assertThat(search(name + " blue")).extracting(c -> c.get("id").asLong()).containsExactly(red);

        // The page of any variation offers the others, at their own price and stock.
        JsonNode page = send(get("/api/products/" + redL), null, null, 200);
        assertThat(page.get("variations").get("dim1").asText()).isEqualTo("Colour");
        assertThat(page.get("variations").get("dim2").asText()).isEqualTo("Size");
        assertThat(page.get("variations").get("option2").asText()).isEqualTo("L");
        assertThat(options(page)).containsExactly("Red/M", "Red/L", "Blue/M");
        JsonNode blueOption = page.get("variations").get("options").get(2);
        assertThat(blueOption.get("productId").asLong()).isEqualTo(blue);
        assertThat(blueOption.get("inStock").asBoolean()).isFalse();
        assertThat(page.get("variations").get("options").get(1).get("price").decimalValue()).isEqualByComparingTo("13.00");

        // The cart and the order say which variation was bought.
        Account buyer = customer();
        addToCart(buyer, redL, 2);
        JsonNode cart = send(get("/api/cart"), buyer.token(), null, 200);
        assertThat(cart.get("items").get(0).get("variation").asText()).isEqualTo("Colour: Red, Size: L");
        JsonNode order = checkout(buyer, null, 201).get("orders").get(0);
        assertThat(order.get("items").get(0).get("variation").asText()).isEqualTo("Colour: Red, Size: L");
        assertThat(products.findById(redL).orElseThrow().getStock()).isEqualTo(2);
        assertThat(products.findById(red).orElseThrow().getStock()).isEqualTo(5);

        // Renaming a dimension relabels every variation; the order keeps what was bought.
        family(seller, red, "Color", "Size", "Red", "M", 200);
        assertThat(products.findById(redL).orElseThrow().getVariation()).isEqualTo("Color: Red, Size: L");
        assertThat(order(buyer, id(order)).get("items").get(0).get("variation").asText()).isEqualTo("Colour: Red, Size: L");
    }

    @Test
    void whenTheFirstVariationIsHiddenAnotherStandsForTheFamily() throws Exception {
        String name = unique("Variation Mug");
        Account seller = seller();
        long first = listProduct(seller, name, "8.00", 5);
        family(seller, first, "Colour", null, "White", null, 200);
        long black = variation(seller, first, "Black", null, "9.00", 5, 201);

        send(delete("/api/seller/products/" + first), seller.token(), null, 200);
        JsonNode cards = search(name);
        assertThat(cards).extracting(c -> c.get("id").asLong()).containsExactly(black);
        assertThat(cards.get(0).get("variationCount").asInt()).isEqualTo(1);
        assertThat(options(send(get("/api/products/" + black), null, null, 200))).containsExactly("Black");
        // Seller Central still lists both, the hidden one marked.
        JsonNode mine = send(get("/api/seller/products/" + black), seller.token(), null, 200);
        assertThat(options(mine)).containsExactly("White", "Black");
        assertThat(mine.get("variations").get("options").get(0).get("active").asBoolean()).isFalse();
    }

    @Test
    void anotherSellersOfferOnAVariationCarriesItsLabel() throws Exception {
        String name = unique("Variation Scarf");
        Account a = seller();
        Account b = seller();
        long grey = listProduct(a, name, "20.00", 5);
        family(a, grey, "Colour", null, "Grey", null, 200);
        long green = variation(a, grey, "Green", null, "20.00", 5, 201);
        long offer = send(post("/api/seller/offers/" + green), b.token(),
                Map.of("price", "18.00", "stock", 2, "condition", "NEW"), 201).get("id").asLong();
        assertThat(products.findById(offer).orElseThrow().getVariation()).isEqualTo("Colour: Green");

        // The picker shows the green variation at its buy-box price (B's offer).
        JsonNode page = send(get("/api/products/" + grey), null, null, 200);
        assertThat(page.get("variations").get("options").get(1).get("price").decimalValue()).isEqualByComparingTo("18.00");
        // B can't manage A's variations, from its offer or A's page.
        family(b, offer, "Colour", null, "Teal", null, 400);
        family(b, green, "Colour", null, "Teal", null, 404);
        variation(b, green, "Teal", null, "5.00", 1, 404);
    }

    @Test
    void optionsMustBeCompleteAndUnique() throws Exception {
        Account seller = seller();
        long p = listProduct(seller, unique("Variation Sock"), "5.00", 5);
        variation(seller, p, "Red", null, "5.00", 1, 400); // no family yet
        family(seller, p, "Size", "Size", "S", "S", 400); // same dimension twice
        family(seller, p, "Colour", "Size", "Red", null, 400); // missing size
        family(seller, p, "Colour", "Size", "Red", "S", 200);
        variation(seller, p, "red", "s", "5.00", 1, 409); // same options, any case
        variation(seller, p, "Red", null, "5.00", 1, 400);
        variation(seller, p, "Red", "M", "5.00", 1, 201);
        family(seller, p, "Colour", null, "Red", null, 400); // can't drop a dimension with others there
    }

    @Test
    void leavingTheFamilyMakesTheLastOneAProductOnItsOwn() throws Exception {
        String name = unique("Variation Cap");
        Account seller = seller();
        long a = listProduct(seller, name, "10.00", 5);
        family(seller, a, "Colour", null, "Navy", null, 200);
        long b = variation(seller, a, "Olive", null, "10.00", 5, 201);
        assertThat(search(name)).hasSize(1);

        JsonNode left = send(delete("/api/seller/products/" + b + "/family"), seller.token(), null, 200);
        assertThat(left.get("variation").isNull()).isTrue();
        assertThat(products.findById(a).orElseThrow().getFamilyId()).isNull();
        assertThat(search(name)).hasSize(2);
        assertThat(send(get("/api/products/" + a), null, null, 200).get("variations").isNull()).isTrue();
    }

    @Test
    void adminsManageVariationsOfPacificsOwnProducts() throws Exception {
        String name = unique("House Variation Pen");
        long pen = send(post("/api/admin/products"), admin(), Map.of("name", name, "price", "2.00", "stock", 50), 201)
                .get("id").asLong();
        send(put("/api/admin/products/" + pen + "/family"), admin(), Map.of("dim1", "Ink", "option1", "Black"), 200);
        long blue = send(post("/api/admin/products/" + pen + "/variations"), admin(),
                Map.of("option1", "Blue", "price", "2.20", "stock", 40), 201).get("id").asLong();
        JsonNode page = send(get("/api/products/" + blue), null, null, 200);
        assertThat(page.get("sellerName").asText()).isEqualTo("Pacific");
        assertThat(options(page)).containsExactly("Black", "Blue");
    }
}
