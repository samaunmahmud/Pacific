package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacific.marketplace.domain.Product;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketplaceTest extends IntegrationTest {

    /** A registered customer who has applied to sell. */
    record Seller(String token, long id, String slug) {
    }

    private JsonNode send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, String token,
                          Object body, int expected) throws Exception {
        if (token != null) b = bearer(b, token);
        if (body != null) b = b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        MvcResult r = mvc.perform(b).andExpect(status().is(expected)).andReturn();
        String text = r.getResponse().getContentAsString();
        return text.isEmpty() ? null : json.readTree(text);
    }

    private Seller pendingSeller(String storeName) throws Exception {
        String token = registerCustomer();
        JsonNode s = send(post("/api/seller/apply"), token, Map.of("storeName", storeName, "description", "We sell things"), 201);
        return new Seller(token, s.get("id").asLong(), s.get("slug").asText());
    }

    private Seller approvedSeller(String storeName) throws Exception {
        Seller s = pendingSeller(storeName);
        send(patch("/api/admin/sellers/" + s.id() + "/status"), adminToken(), Map.of("status", "APPROVED"), 200);
        return s;
    }

    private long listProduct(Seller s, String name, String price, int stock) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("price", price);
        body.put("stock", stock);
        return send(post("/api/seller/products"), s.token(), body, 201).get("id").asLong();
    }

    private void addToCart(String token, long productId, int qty, int expected) throws Exception {
        send(post("/api/cart/items"), token, Map.of("productId", productId, "quantity", qty), expected);
    }

    private JsonNode checkout(String token) throws Exception {
        return send(post("/api/orders"), token, address(), 201);
    }

    private void setSellerStatus(Seller s, String status, String token) throws Exception {
        send(patch("/api/seller/orders/" + s.id() + "/status"), token, Map.of("status", status), 200);
    }

    private void advance(String sellerToken, long orderId, String... statuses) throws Exception {
        for (String st : statuses) {
            send(patch("/api/seller/orders/" + orderId + "/status"), sellerToken, Map.of("status", st), 200);
        }
    }

    // ---------------------------------------------------------------- onboarding & visibility

    @Test
    void sellerMustBeApprovedBeforeSelling() throws Exception {
        Seller s = pendingSeller("Acme Gadgets");
        assertThat(s.slug()).isEqualTo("acme-gadgets");

        // a second application, or a reserved / duplicate store name, is refused
        send(post("/api/seller/apply"), s.token(), Map.of("storeName", "Acme Again"), 409);
        send(post("/api/seller/apply"), registerCustomer(), Map.of("storeName", "acme gadgets"), 409);
        send(post("/api/seller/apply"), registerCustomer(), Map.of("storeName", "Pacific"), 409);

        // pending: can read own status but not list products or open Seller Central data
        send(get("/api/seller/me"), s.token(), null, 200);
        send(post("/api/seller/products"), s.token(), Map.of("name", "X", "price", "5.00", "stock", 1), 403);
        send(get("/api/seller/stats"), s.token(), null, 403);
        // never a seller at all
        send(get("/api/seller/me"), registerCustomer(), null, 204);

        String admin = adminToken();
        send(patch("/api/admin/sellers/" + s.id() + "/status"), admin, Map.of("status", "REJECTED", "note", "Too vague"), 200);
        send(post("/api/seller/products"), s.token(), Map.of("name", "X", "price", "5.00", "stock", 1), 403);
        // a rejected applicant may apply again with a better description
        send(post("/api/seller/apply"), s.token(), Map.of("storeName", "Acme Gadgets", "description", "Detailed"), 201);
        send(get("/api/seller/me"), s.token(), null, 200);
        send(patch("/api/admin/sellers/" + s.id() + "/status"), admin, Map.of("status", "APPROVED"), 200);
        listProduct(s, "Widget", "12.00", 5);
    }

    @Test
    void sellerListingsAreVisibleOnlyWhileTheSellerIsApproved() throws Exception {
        Seller s = approvedSeller("Visible Store");
        long productId = listProduct(s, "Visible Widget", "12.00", 5);
        String customer = registerCustomer();

        mvc.perform(get("/api/products/" + productId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerName").value("Visible Store"))
                .andExpect(jsonPath("$.sellerSlug").value("visible-store"));
        mvc.perform(get("/api/products").param("seller", "visible-store")).andExpect(jsonPath("$.totalItems").value(1));
        addToCart(customer, productId, 1, 200);

        // suspension hides everything at once, and blocks the checkout of items already in a cart
        String admin = adminToken();
        send(patch("/api/admin/sellers/" + s.id() + "/status"), admin, Map.of("status", "SUSPENDED", "note", "Policy"), 200);
        mvc.perform(get("/api/products/" + productId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products").param("seller", "visible-store")).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/products").param("q", "Visible Widget")).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/sellers/visible-store")).andExpect(status().isNotFound());
        addToCart(registerCustomer(), productId, 1, 404);
        send(post("/api/orders"), customer, address(), 409);
        send(get("/api/seller/stats"), s.token(), null, 403);

        send(patch("/api/admin/sellers/" + s.id() + "/status"), admin, Map.of("status", "APPROVED"), 200);
        mvc.perform(get("/api/products/" + productId)).andExpect(status().isOk());
        assertThat(stockOf(productRepo.findById(productId).orElseThrow())).isEqualTo(5); // failed checkout took nothing
    }

    @Test
    void sellersOnlySeeAndChangeTheirOwnThings() throws Exception {
        Seller a = approvedSeller("Alpha Store");
        Seller b = approvedSeller("Beta Store");
        long aProduct = listProduct(a, "Alpha Item", "10.00", 5);
        String buyer = registerCustomer();
        addToCart(buyer, aProduct, 1, 200);
        long orderId = checkout(buyer).get("orders").get(0).get("id").asLong();

        // B can't read or change A's product or A's order (they simply don't exist for B)
        send(get("/api/seller/products/" + aProduct), b.token(), null, 404);
        send(put("/api/seller/products/" + aProduct), b.token(),
                Map.of("name", "Hijacked", "price", "1.00", "stock", 1), 404);
        send(patch("/api/seller/products/" + aProduct + "/stock"), b.token(), Map.of("stock", 0), 404);
        send(delete("/api/seller/products/" + aProduct), b.token(), null, 404);
        send(get("/api/seller/orders/" + orderId), b.token(), null, 404);
        send(patch("/api/seller/orders/" + orderId + "/status"), b.token(), Map.of("status", "PROCESSING"), 404);
        mvc.perform(bearer(get("/api/seller/orders"), b.token())).andExpect(jsonPath("$.totalItems").value(0));
        // A does see it
        mvc.perform(bearer(get("/api/seller/orders"), a.token())).andExpect(jsonPath("$.totalItems").value(1));
        // an admin isn't a customer account, so Seller Central is closed to them
        send(get("/api/seller/me"), adminToken(), null, 403);
        send(get("/api/seller/me"), null, null, 401);
    }

    @Test
    void sellersCannotBuyTheirOwnProducts() throws Exception {
        Seller s = approvedSeller("Self Store");
        long own = listProduct(s, "My Own Item", "10.00", 5);
        addToCart(s.token(), own, 1, 409);
        // ...but they can shop from others
        long other = listProduct(approvedSeller("Other Store"), "Other Item", "10.00", 5);
        addToCart(s.token(), other, 1, 200);
    }

    // ---------------------------------------------------------------- split checkout

    @Test
    void checkoutSplitsIntoOneOrderPerSellerWithPerSellerShipping() throws Exception {
        Seller a = approvedSeller("Split A");
        Seller b = approvedSeller("Split B");
        Product house = product("House Item", "20.00", 5);
        long aItem = listProduct(a, "A Item", "60.00", 5);
        long bItem = listProduct(b, "B Item", "10.00", 5);

        String buyer = registerCustomer();
        addToCart(buyer, house.getId(), 1, 200);
        addToCart(buyer, aItem, 1, 200);
        addToCart(buyer, bItem, 1, 200);

        // the cart already shows per-seller shipping: £3.99 + free (£60 ≥ £50) + £3.99
        mvc.perform(bearer(get("/api/cart"), buyer))
                .andExpect(jsonPath("$.shipments.length()").value(3))
                .andExpect(jsonPath("$.subtotal").value(90.00))
                .andExpect(jsonPath("$.shipping").value(7.98))
                .andExpect(jsonPath("$.total").value(97.98));

        JsonNode result = checkout(buyer);
        JsonNode orders = result.get("orders");
        assertThat(orders).hasSize(3);
        assertThat(result.get("total").decimalValue()).isEqualByComparingTo("97.98");
        String ref = result.get("checkoutRef").asText();
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode o : orders) {
            assertThat(o.get("checkoutRef").asText()).isEqualTo(ref);
            assertThat(o.get("items")).hasSize(1);
            sum = sum.add(o.get("total").decimalValue());
        }
        assertThat(sum).isEqualByComparingTo("97.98");
        assertThat(orders.get(0).get("sellerName").asText()).isEqualTo("Pacific");
        assertThat(orders.get(1).get("sellerName").asText()).isEqualTo("Split A");
        mvc.perform(bearer(get("/api/cart"), buyer)).andExpect(jsonPath("$.itemCount").value(0));
        mvc.perform(bearer(get("/api/orders"), buyer)).andExpect(jsonPath("$.length()").value(3));
        // each seller sees only their own slice
        mvc.perform(bearer(get("/api/seller/orders"), a.token())).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].total").value(60.00));
        mvc.perform(bearer(get("/api/seller/stats"), b.token())).andExpect(jsonPath("$.unitsSold").value(1))
                .andExpect(jsonPath("$.ordersByStatus.PLACED").value(1));
    }

    // ---------------------------------------------------------------- commission & payouts

    @Test
    void deliveredOrdersEarnTheSellerNetOfCommissionAndPayoutsCannotOverdraw() throws Exception {
        Seller s = approvedSeller("Ledger Store");
        String admin = adminToken();
        long item = listProduct(s, "Ledger Item", "30.00", 10);
        String buyer = registerCustomer();

        // order 1 at the default 10% commission: £30 + £3.99 shipping
        addToCart(buyer, item, 1, 200);
        long o1 = checkout(buyer).get("orders").get(0).get("id").asLong();
        advance(s.token(), o1, "PROCESSING", "SHIPPED");
        mvc.perform(bearer(get("/api/seller/earnings"), s.token())).andExpect(jsonPath("$.balance").value(0.00)); // not delivered yet
        advance(s.token(), o1, "DELIVERED");
        mvc.perform(bearer(get("/api/seller/earnings"), s.token()))
                .andExpect(jsonPath("$.sales").value(33.99))
                .andExpect(jsonPath("$.commission").value(3.00))
                .andExpect(jsonPath("$.balance").value(30.99));

        // raise this seller's commission to 20%; the already-placed rate on order 1 must not change
        send(put("/api/admin/sellers/" + s.id() + "/commission"), admin, Map.of("percent", "20.00"), 200);
        addToCart(buyer, item, 2, 200); // £60, free shipping
        long o2 = checkout(buyer).get("orders").get(0).get("id").asLong();
        advance(s.token(), o2, "PROCESSING", "SHIPPED", "DELIVERED");
        mvc.perform(bearer(get("/api/seller/earnings"), s.token()))
                .andExpect(jsonPath("$.commission").value(15.00)) // 3.00 + 12.00
                .andExpect(jsonPath("$.balance").value(78.99));   // 30.99 + (60 - 12)

        // a delivered order is final: no double-booking, no more moves
        send(patch("/api/seller/orders/" + o2 + "/status"), s.token(), Map.of("status", "DELIVERED"), 409);
        mvc.perform(bearer(get("/api/seller/earnings"), s.token())).andExpect(jsonPath("$.balance").value(78.99));

        // payouts: never more than the balance
        send(post("/api/admin/sellers/" + s.id() + "/payouts"), admin, Map.of("amount", "500.00"), 409);
        send(post("/api/admin/sellers/" + s.id() + "/payouts"), admin, Map.of("amount", "-1"), 400);
        send(post("/api/admin/sellers/" + s.id() + "/payouts"), admin, Map.of("amount", "70.00", "note", "Bank transfer"), 201);
        mvc.perform(bearer(get("/api/seller/earnings"), s.token()))
                .andExpect(jsonPath("$.balance").value(8.99)).andExpect(jsonPath("$.payouts").value(70.00));
        send(post("/api/admin/sellers/" + s.id() + "/payouts"), admin, Map.of("amount", "9.00"), 409);
        send(post("/api/admin/sellers/" + s.id() + "/payouts"), admin, Map.of("amount", "8.99"), 201);
        mvc.perform(bearer(get("/api/admin/sellers/" + s.id()), admin)).andExpect(jsonPath("$.balance").value(0.00));
        mvc.perform(bearer(get("/api/admin/stats"), admin)).andExpect(jsonPath("$.commissionEarned").value(15.00));
        // only admins can pay out
        send(post("/api/admin/sellers/" + s.id() + "/payouts"), s.token(), Map.of("amount", "1.00"), 403);
    }

    @Test
    void cancelledAndHouseOrdersNeverTouchTheLedger() throws Exception {
        Seller s = approvedSeller("Cancel Store");
        long item = listProduct(s, "Cancel Item", "30.00", 5);
        Product house = product("House Thing", "20.00", 5);
        String buyer = registerCustomer();
        addToCart(buyer, item, 1, 200);
        addToCart(buyer, house.getId(), 1, 200);
        JsonNode orders = checkout(buyer).get("orders");
        // orders follow cart order (the seller's item went in first), so pick them by store rather than by position
        long houseOrder = -1, sellerOrder = -1;
        for (JsonNode o : orders) {
            if ("Pacific".equals(o.get("sellerName").asText())) houseOrder = o.get("id").asLong();
            else sellerOrder = o.get("id").asLong();
        }

        // the seller can cancel their own order: stock goes back, nothing is earned
        advance(s.token(), sellerOrder, "CANCELLED");
        assertThat(stockOf(productRepo.findById(item).orElseThrow())).isEqualTo(5);
        mvc.perform(bearer(get("/api/seller/earnings"), s.token())).andExpect(jsonPath("$.balance").value(0.00));

        // a house order is delivered by an admin and creates no seller earnings
        String admin = adminToken();
        for (String st : new String[]{"PROCESSING", "SHIPPED", "DELIVERED"}) {
            send(patch("/api/admin/orders/" + houseOrder + "/status"), admin, Map.of("status", st), 200);
        }
        mvc.perform(bearer(get("/api/admin/stats"), admin)).andExpect(jsonPath("$.commissionEarned").value(0));
    }

    // ---------------------------------------------------------------- seller ratings

    @Test
    void buyersCanRateASellerOnlyAfterDeliveryAndTheAverageFollows() throws Exception {
        Seller s = approvedSeller("Rated Store");
        long item = listProduct(s, "Rated Item", "10.00", 10);
        String one = registerCustomer();
        String two = registerCustomer();

        addToCart(one, item, 1, 200);
        long o1 = checkout(one).get("orders").get(0).get("id").asLong();
        addToCart(two, item, 1, 200);
        long o2 = checkout(two).get("orders").get(0).get("id").asLong();

        send(put("/api/sellers/rated-store/rating"), one, Map.of("rating", 5), 403); // not delivered yet
        send(put("/api/sellers/rated-store/rating"), s.token(), Map.of("rating", 5), 403); // own store
        mvc.perform(bearer(get("/api/sellers/rated-store"), one)).andExpect(jsonPath("$.eligibility.canRate").value(false));

        advance(s.token(), o1, "PROCESSING", "SHIPPED", "DELIVERED");
        advance(s.token(), o2, "PROCESSING", "SHIPPED", "DELIVERED");
        mvc.perform(bearer(get("/api/sellers/rated-store"), one)).andExpect(jsonPath("$.eligibility.canRate").value(true));
        send(put("/api/sellers/rated-store/rating"), one, Map.of("rating", 5, "comment", "Fast"), 200);
        send(put("/api/sellers/rated-store/rating"), two, Map.of("rating", 2), 200);
        send(put("/api/sellers/rated-store/rating"), two, Map.of("rating", 3), 200); // updates, doesn't add
        send(put("/api/sellers/rated-store/rating"), two, Map.of("rating", 9), 400);

        mvc.perform(get("/api/sellers/rated-store"))
                .andExpect(jsonPath("$.summary.count").value(2))
                .andExpect(jsonPath("$.summary.average").value(4.0))
                .andExpect(jsonPath("$.seller.ratingAvg").value(4.0))
                .andExpect(jsonPath("$.seller.productCount").value(1));
        send(delete("/api/sellers/rated-store/rating"), one, null, 204);
        mvc.perform(get("/api/sellers/rated-store")).andExpect(jsonPath("$.seller.ratingAvg").value(3.0));
    }

    // ---------------------------------------------------------------- wishlist, deals, recently viewed

    @Test
    void wishlistSavesProductsAndDropsHiddenOnes() throws Exception {
        String customer = registerCustomer();
        Product a = product("Wish A", "10.00", 5);
        Product b = product("Wish B", "10.00", 5);
        send(put("/api/wishlist/" + a.getId()), customer, null, 204);
        send(put("/api/wishlist/" + a.getId()), customer, null, 204); // idempotent
        send(put("/api/wishlist/" + b.getId()), customer, null, 204);
        send(put("/api/wishlist/999999"), customer, null, 404);
        mvc.perform(bearer(get("/api/wishlist"), customer)).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Wish B")); // newest first
        mvc.perform(bearer(get("/api/wishlist/ids"), customer)).andExpect(jsonPath("$.length()").value(2));

        b.setActive(false);
        productRepo.saveAndFlush(b);
        mvc.perform(bearer(get("/api/wishlist"), customer)).andExpect(jsonPath("$.length()").value(1));
        send(delete("/api/wishlist/" + a.getId()), customer, null, 204);
        mvc.perform(bearer(get("/api/wishlist"), customer)).andExpect(jsonPath("$.length()").value(0));
        send(get("/api/wishlist"), null, null, 401);
    }

    @Test
    void dealsShowDiscountedProductsAndValidateThePrices() throws Exception {
        Seller s = approvedSeller("Deals Store");
        Map<String, Object> deal = new LinkedHashMap<>();
        deal.put("name", "Deal Item"); deal.put("price", "75.00"); deal.put("listPrice", "100.00"); deal.put("stock", 3);
        long dealId = send(post("/api/seller/products"), s.token(), deal, 201).get("id").asLong();
        listProduct(s, "Full Price Item", "20.00", 3);

        mvc.perform(get("/api/products/" + dealId)).andExpect(jsonPath("$.discountPercent").value(25))
                .andExpect(jsonPath("$.listPrice").value(100.00));
        mvc.perform(get("/api/products").param("deals", "true").param("sort", "discount"))
                .andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].name").value("Deal Item"));

        // a "was" price that isn't higher than the price is refused; removing it ends the deal
        deal.put("listPrice", "70.00");
        send(put("/api/seller/products/" + dealId), s.token(), deal, 400);
        deal.put("listPrice", null);
        send(put("/api/seller/products/" + dealId), s.token(), deal, 200);
        mvc.perform(get("/api/products").param("deals", "true")).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void recentlyViewedBatchKeepsRequestedOrderAndSkipsHiddenProducts() throws Exception {
        Product a = product("Batch A", "10.00", 5);
        Product b = product("Batch B", "10.00", 5);
        Product hidden = product("Batch Hidden", "10.00", 5);
        hidden.setActive(false);
        productRepo.saveAndFlush(hidden);
        mvc.perform(get("/api/products/batch").param("ids", b.getId() + "," + hidden.getId() + "," + a.getId() + ",999999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Batch B")).andExpect(jsonPath("$[1].name").value("Batch A"));
    }

    // ---------------------------------------------------------------- Q&A

    @Test
    void questionsAreAnsweredBySellerBuyersAndPacificOnly() throws Exception {
        Seller s = approvedSeller("Ask Store");
        long item = listProduct(s, "Ask Item", "10.00", 5);
        String asker = registerCustomer();
        String buyer = registerCustomer();
        String stranger = registerCustomer();
        String admin = adminToken();

        addToCart(buyer, item, 1, 200);
        checkout(buyer);

        long q = send(post("/api/products/" + item + "/questions"), asker, Map.of("text", "Is it waterproof?"), 201)
                .get("id").asLong();
        send(post("/api/products/" + item + "/questions"), s.token(), Map.of("text", "Self question"), 403);
        send(post("/api/products/" + item + "/questions"), null, Map.of("text", "Anon"), 401);
        send(post("/api/products/" + item + "/questions"), asker, Map.of("text", " "), 400);
        mvc.perform(bearer(get("/api/seller/questions"), s.token())).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].productName").value("Ask Item"));

        send(post("/api/questions/" + q + "/answers"), stranger, Map.of("text", "I think so"), 403);
        send(post("/api/questions/" + q + "/answers"), s.token(), Map.of("text", "Yes, IP67"), 201);
        send(post("/api/questions/" + q + "/answers"), buyer, Map.of("text", "Works in the rain for me"), 201);
        send(post("/api/questions/" + q + "/answers"), admin, Map.of("text", "Confirmed by Pacific"), 201);
        mvc.perform(bearer(get("/api/seller/questions"), s.token())).andExpect(jsonPath("$.length()").value(0));

        JsonNode list = json.readTree(mvc.perform(get("/api/products/" + item + "/questions")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode answers = list.get("questions").get(0).get("answers");
        assertThat(answers).hasSize(3);
        assertThat(answers.get(0).get("label").asText()).isEqualTo("SELLER");
        assertThat(answers.get(1).get("label").asText()).isEqualTo("BUYER");
        assertThat(answers.get(2).get("label").asText()).isEqualTo("PACIFIC");
        assertThat(list.get("canAsk").asBoolean()).isFalse(); // anonymous

        // what each viewer is allowed to do
        mvc.perform(bearer(get("/api/products/" + item + "/questions"), stranger))
                .andExpect(jsonPath("$.canAsk").value(true)).andExpect(jsonPath("$.canAnswer").value(false));
        mvc.perform(bearer(get("/api/products/" + item + "/questions"), buyer)).andExpect(jsonPath("$.canAnswer").value(true));
        mvc.perform(bearer(get("/api/products/" + item + "/questions"), s.token()))
                .andExpect(jsonPath("$.canAsk").value(false)).andExpect(jsonPath("$.canAnswer").value(true));

        // deleting: authors and admins only
        long answerId = answers.get(1).get("id").asLong();
        send(delete("/api/answers/" + answerId), stranger, null, 403);
        send(delete("/api/answers/" + answerId), buyer, null, 204);
        send(delete("/api/questions/" + q), stranger, null, 403);
        send(delete("/api/questions/" + q), admin, null, 204);
        mvc.perform(get("/api/products/" + item + "/questions")).andExpect(jsonPath("$.questions.length()").value(0));
    }

    @Test
    void adminManagesSellersAndCommission() throws Exception {
        String admin = adminToken();
        Seller s = pendingSeller("Managed Store");
        mvc.perform(bearer(get("/api/admin/sellers").param("status", "PENDING"), admin))
                .andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].seller.storeName").value("Managed Store"));
        mvc.perform(bearer(get("/api/admin/stats"), admin)).andExpect(jsonPath("$.pendingSellers").value(1));
        send(patch("/api/admin/sellers/" + s.id() + "/status"), admin, Map.of("status", "PENDING"), 400);

        send(put("/api/admin/settings/commission"), admin, Map.of("percent", "12.50"), 200);
        send(put("/api/admin/settings/commission"), admin, Map.of("percent", "150"), 400);
        mvc.perform(bearer(get("/api/admin/sellers/" + s.id()), admin))
                .andExpect(jsonPath("$.seller.commissionPercent").value(12.50))
                .andExpect(jsonPath("$.seller.commissionOverridden").value(false));
        send(put("/api/admin/sellers/" + s.id() + "/commission"), admin, Map.of("percent", "5"), 200);
        mvc.perform(bearer(get("/api/admin/sellers/" + s.id()), admin)).andExpect(jsonPath("$.seller.commissionPercent").value(5.0))
                .andExpect(jsonPath("$.seller.commissionOverridden").value(true));
        // a null percent clears the override again
        Map<String, Object> clear = new LinkedHashMap<>();
        clear.put("percent", null);
        send(put("/api/admin/sellers/" + s.id() + "/commission"), admin, clear, 200);
        mvc.perform(bearer(get("/api/admin/sellers/" + s.id()), admin)).andExpect(jsonPath("$.seller.commissionPercent").value(12.50));
        // customers can't touch any of it
        send(get("/api/admin/sellers"), s.token(), null, 403);
        send(put("/api/admin/settings/commission"), s.token(), Map.of("percent", "0"), 403);
    }
}
