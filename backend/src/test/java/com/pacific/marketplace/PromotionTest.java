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

/** Lightning Deals, coupons and promo codes: pricing, atomic claims at checkout, and giving them back on cancel. */
class PromotionTest extends CommittedFlowTestBase {

    private Account store() throws Exception {
        Account owner = customer();
        JsonNode s = send(post("/api/seller/apply"), owner.token(),
                Map.of("storeName", "Promo Store " + UUID.randomUUID().toString().substring(0, 6), "description", "x"), 201);
        send(patch("/api/admin/sellers/" + s.get("id").asLong() + "/status"), admin(), Map.of("status", "APPROVED"), 200);
        return owner;
    }

    private JsonNode deal(Account seller, long product, String price, int quantity, int hours, int expected) throws Exception {
        return send(post("/api/seller/promotions/deals"), seller.token(),
                Map.of("productId", product, "price", price, "quantity", quantity, "hours", hours), expected);
    }

    private JsonNode offer(long product, Account viewer) throws Exception {
        return send(get("/api/products/" + product + "/offers"), viewer == null ? null : viewer.token(), null, 200).get(0);
    }

    private JsonNode cart(Account c) throws Exception {
        return send(get("/api/cart"), c.token(), null, 200);
    }

    private JsonNode cartWithCode(Account c, String code) throws Exception {
        return send(get("/api/cart").param("promo", code), c.token(), null, 200);
    }

    private JsonNode checkoutWithCode(Account c, String code, int expected) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>(Map.of("name", "Casey", "line1", "1 High St", "city", "Uxbridge",
                "postcode", "UB8 1AA", "country", "United Kingdom"));
        if (code != null) req.put("promoCode", code);
        return send(post("/api/orders"), c.token(), req, expected);
    }

    @Test
    void aLightningDealSellsItsUnitsAtTheDealPriceThenStops() throws Exception {
        Account seller = store();
        long lamp = listProduct(seller, "Deal lamp", "40.00", 10);
        JsonNode created = deal(seller, lamp, "30.00", 2, 2, 201);
        assertThat(created.get("status").asText()).isEqualTo("LIVE");

        JsonNode shown = offer(lamp, null);
        assertThat(shown.get("price").decimalValue()).isEqualByComparingTo("30.00"); // the buy box charges the deal price
        assertThat(shown.get("deal").get("percentOff").asInt()).isEqualTo(25);
        JsonNode card = send(get("/api/products").param("q", "Deal lamp"), null, null, 200).get("items").get(0);
        assertThat(card.get("boxPrice").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(card.get("deal").get("price").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(send(get("/api/products").param("deals", "true").param("q", "Deal lamp"), null, null, 200).get("items")).hasSize(1);

        Account first = customer();
        addToCart(first, lamp, 2);
        JsonNode line = cart(first).get("items").get(0);
        assertThat(line.get("unitPrice").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(line.get("listUnitPrice").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(line.get("promotion").asText()).isEqualTo("Lightning Deal");
        JsonNode order = checkout(first, null, 201).get("orders").get(0);
        JsonNode item = order.get("items").get(0);
        assertThat(item.get("unitPrice").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(item.get("listUnitPrice").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(order.get("subtotal").decimalValue()).isEqualByComparingTo("60.00");

        // Both deal units have gone: the next shopper pays the regular price, and the buy box says so.
        Account second = customer();
        addToCart(second, lamp, 1);
        assertThat(cart(second).get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("40.00");
        assertThat(offer(lamp, null).get("price").decimalValue()).isEqualByComparingTo("40.00");

        // Cancelling gives the units back to the deal.
        send(post("/api/orders/" + id(order) + "/cancel"), first.token(), null, 200);
        assertThat(cart(second).get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("30.00");
    }

    @Test
    void dealsNeedARealDiscountStockAndNoOverlap() throws Exception {
        Account seller = store();
        long mug = listProduct(seller, "Deal mug", "10.00", 3);
        deal(seller, mug, "10.00", 1, 2, 400);   // not below the price
        deal(seller, mug, "9.80", 1, 2, 400);    // less than 5% off
        deal(seller, mug, "8.00", 4, 2, 400);    // more than in stock
        deal(seller, mug, "8.00", 1, 13, 400);   // too long
        long id = deal(seller, mug, "8.00", 1, 2, 201).get("id").asLong();
        deal(seller, mug, "7.00", 1, 2, 409);    // overlaps
        Account other = store();
        deal(other, mug, "8.00", 1, 2, 404);     // not their listing

        JsonNode ended = send(post("/api/seller/promotions/deals/" + id + "/end"), seller.token(), null, 200);
        assertThat(ended.get("status").asText()).isEqualTo("ENDED");
        assertThat(offer(mug, null).get("deal").isNull()).isTrue();
        assertThat(offer(mug, null).get("price").decimalValue()).isEqualByComparingTo("10.00");
    }

    @Test
    void aClippedCouponTakesItsPercentOffOncePerCustomerWithinItsBudget() throws Exception {
        Account seller = store();
        long rug = listProduct(seller, "Coupon rug", "50.00", 10);
        long couponId = send(post("/api/seller/promotions/coupons"), seller.token(),
                Map.of("productId", rug, "percentOff", 10, "budget", 1, "days", 7), 201).get("id").asLong();

        Account bea = customer();
        assertThat(offer(rug, bea).get("coupon").get("clipped").asBoolean()).isFalse();
        addToCart(bea, rug, 1);
        assertThat(cart(bea).get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("50.00"); // not clipped yet
        send(post("/api/coupons/" + couponId + "/clip"), bea.token(), null, 204);
        assertThat(offer(rug, bea).get("coupon").get("clipped").asBoolean()).isTrue();
        assertThat(cart(bea).get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("45.00");
        JsonNode order = checkout(bea, null, 201).get("orders").get(0);
        assertThat(order.get("items").get(0).get("promotion").asText()).isEqualTo("Coupon 10%");

        // The budget of one is used: nobody else can clip it, and Bea's next order is full price.
        Account sam = customer();
        send(post("/api/coupons/" + couponId + "/clip"), sam.token(), null, 404);
        addToCart(bea, rug, 1);
        assertThat(cart(bea).get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("50.00");

        // Cancelling gives the use back.
        send(post("/api/orders/" + id(order) + "/cancel"), bea.token(), null, 200);
        assertThat(cart(bea).get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("45.00");
    }

    @Test
    void aPromoCodeTakesItsPercentOffItsStoresItemsOncePerCustomer() throws Exception {
        Account seller = store();
        Account otherStore = store();
        long vase = listProduct(seller, "Code vase", "20.00", 10);
        long bowl = listProduct(otherStore, "Code bowl", "20.00", 10);
        String code = "SAVE" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Map<String, Object> req = new LinkedHashMap<>(Map.of("code", code, "percentOff", 15, "minSpend", "30.00", "days", 7));
        send(post("/api/seller/promotions/codes"), seller.token(), req, 201);
        send(post("/api/seller/promotions/codes"), otherStore.token(), req, 409); // codes are unique

        Account bea = customer();
        addToCart(bea, bowl, 1);
        assertThat(cartWithCode(bea, code).get("promoError").asText()).contains("none in your cart");
        addToCart(bea, vase, 1);
        assertThat(cartWithCode(bea, code).get("promoError").asText()).contains("Spend £30.00");
        addToCart(bea, vase, 1);
        JsonNode preview = cartWithCode(bea, code.toLowerCase());
        assertThat(preview.get("promoError").isNull()).isTrue();
        assertThat(preview.get("promo").get("discount").decimalValue()).isEqualByComparingTo("6.00");
        assertThat(preview.get("subtotal").decimalValue()).isEqualByComparingTo("54.00"); // 34 + 20: the bowl is untouched
        checkoutWithCode(bea, "NOPE1234", 400);

        JsonNode placed = checkoutWithCode(bea, code, 201);
        for (JsonNode o : placed.get("orders")) {
            JsonNode item = o.get("items").get(0);
            if (item.get("productName").asText().equals("Code vase")) {
                assertThat(item.get("unitPrice").decimalValue()).isEqualByComparingTo("17.00");
                assertThat(item.get("promotion").asText()).isEqualTo("Code " + code);
            } else {
                assertThat(item.get("unitPrice").decimalValue()).isEqualByComparingTo("20.00");
            }
        }
        addToCart(bea, vase, 2);
        assertThat(cartWithCode(bea, code).get("promoError").asText()).contains("already used");
    }

    @Test
    void aCodeStacksOnADealAndAdminsRunPromotionsForPacificsOwnProducts() throws Exception {
        long house = send(post("/api/admin/products"), admin(), Map.of("name", "House kettle", "price", "100.00", "stock", 5), 201)
                .get("id").asLong();
        send(post("/api/admin/promotions/deals"), admin(), Map.of("productId", house, "price", "80.00", "quantity", 3, "hours", 3), 201);
        String code = "HOUSE" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        send(post("/api/admin/promotions/codes"), admin(), Map.of("code", code, "percentOff", 10, "days", 3), 201);

        Account bea = customer();
        addToCart(bea, house, 1);
        JsonNode line = cartWithCode(bea, code).get("items").get(0);
        assertThat(line.get("unitPrice").decimalValue()).isEqualByComparingTo("72.00"); // 100 → 80 deal → 10% off
        assertThat(line.get("promotion").asText()).isEqualTo("Lightning Deal · Code " + code);
        assertThat(send(get("/api/admin/promotions"), admin(), null, 200).get("deals")).isNotEmpty();

        // Sellers can't touch Pacific's promotions or run them on its products.
        Account seller = store();
        deal(seller, house, "70.00", 1, 2, 404);
    }
}
