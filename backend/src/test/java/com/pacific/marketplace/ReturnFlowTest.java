package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.SentEmail;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Returns and refunds: who may ask, what is refunded (card or directly), what happens to the seller's ledger and the
 * stock, and that a refund can only ever happen once. Not transactional, so refunds really commit.
 */
class ReturnFlowTest extends CommittedFlowTestBase {

    // ---------- helpers ----------

    private JsonNode requestReturn(Account buyer, long orderId, long itemId, int qty, int expected) throws Exception {
        return send(post("/api/orders/" + orderId + "/returns"), buyer.token(),
                Map.of("items", List.of(Map.of("orderItemId", itemId, "quantity", qty)), "reason", "DAMAGED",
                        "comment", "The handle snapped"), expected);
    }

    private JsonNode act(Account seller, long returnId, String action, Object body, int expected) throws Exception {
        return send(post("/api/seller/returns/" + returnId + "/" + action), seller.token(), body, expected);
    }

    private long itemId(JsonNode order) {
        return order.get("items").get(0).get("id").asLong();
    }

    private JsonNode earnings(Account seller) throws Exception {
        return send(get("/api/seller/earnings"), seller.token(), null, 200);
    }

    private List<String> ledgerTypes(Account seller) throws Exception {
        List<String> types = new ArrayList<>();
        earnings(seller).get("entries").get("items").forEach(e -> types.add(e.get("type").asText()));
        return types;
    }

    private BigDecimal balance(Account seller) throws Exception {
        return earnings(seller).get("balance").decimalValue();
    }

    private int stock(long productId) {
        return products.findById(productId).orElseThrow().getStock();
    }

    private JsonNode payment(Account buyer, String checkoutRef) throws Exception {
        return send(get("/api/payments/" + checkoutRef), buyer.token(), null, 200);
    }

    // ---------- the money ----------

    @Test
    void aCustomerReturnsPartOfACardOrderThenTheRestAndEveryPennyBalances() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Stoneware Mug Set", "12.00", 10);
        Account buyer = customer();
        addToCart(buyer, pid, 3);
        JsonNode res = checkout(buyer, "CARD", 201);
        String ref = res.get("checkoutRef").asText();
        long orderId = id(res.get("orders").get(0));
        send(post("/api/payments/" + ref + "/simulate"), buyer.token(), Map.of("outcome", "PAID"), 200);
        for (String s : List.of("PROCESSING", "SHIPPED", "DELIVERED")) setStatus(seller, orderId, s, null, null);
        JsonNode delivered = order(buyer, orderId);
        // 3 x 12.00 = 36.00, plus 3.99 delivery = 39.99; the seller keeps 90% of the goods: 36.00 - 3.60 + 3.99
        assertThat(delivered.get("total").decimalValue()).isEqualByComparingTo("39.99");
        assertThat(balance(seller)).isEqualByComparingTo("36.39");
        assertThat(delivered.get("canReturn").asBoolean()).isTrue();
        assertThat(delivered.get("items").get(0).get("returnableQuantity").asInt()).isEqualTo(3);
        int stockAfterSale = stock(pid);

        // return one of the three: refund the goods only, not delivery
        JsonNode first = requestReturn(buyer, orderId, itemId(delivered), 1, 201);
        assertThat(first.get("status").asText()).isEqualTo("REQUESTED");
        assertThat(order(buyer, orderId).get("items").get(0).get("returnableQuantity").asInt()).isEqualTo(2);
        JsonNode approved = act(seller, first.get("id").asLong(), "approve", Map.of("note", "Post it to 1 Warehouse Way"), 200);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("maxRefund").decimalValue()).isEqualByComparingTo("12.00");
        JsonNode refunded = act(seller, first.get("id").asLong(), "refund", null, 200);
        assertThat(refunded.get("refundAmount").decimalValue()).isEqualByComparingTo("12.00");
        assertThat(payment(buyer, ref).get("refundedAmount").decimalValue()).isEqualByComparingTo("12.00");
        assertThat(stock(pid)).isEqualTo(stockAfterSale + 1); // it went back on sale
        assertThat(ledgerTypes(seller)).contains("REFUND", "COMMISSION_REFUND");
        assertThat(balance(seller)).isEqualByComparingTo("25.59"); // 36.39 - 12.00 + 1.20 commission returned

        // return the other two: this completes the order, so delivery is refunded too
        JsonNode second = requestReturn(buyer, orderId, itemId(delivered), 2, 201);
        JsonNode secondApproved = act(seller, second.get("id").asLong(), "approve", null, 200);
        assertThat(secondApproved.get("maxRefund").decimalValue()).isEqualByComparingTo("27.99"); // 24.00 + 3.99 delivery
        JsonNode secondRefunded = act(seller, second.get("id").asLong(), "refund", null, 200);
        assertThat(secondRefunded.get("refundAmount").decimalValue()).isEqualByComparingTo("27.99");
        assertThat(payment(buyer, ref).get("refundedAmount").decimalValue()).isEqualByComparingTo("39.99"); // all of it
        assertThat(stock(pid)).isEqualTo(stockAfterSale + 3);
        assertThat(balance(seller)).isEqualByComparingTo("0.00"); // sale, commission and refunds all cancel out
        JsonNode e = earnings(seller);
        assertThat(e.get("refunds").decimalValue()).isEqualByComparingTo("39.99");
        assertThat(e.get("commission").decimalValue()).isEqualByComparingTo("0.00"); // every penny of commission came back

        JsonNode after = order(buyer, orderId);
        assertThat(timeline(after)).contains("RETURN_REQUESTED", "RETURN_APPROVED", "REFUNDED");
        assertThat(after.get("canReturn").asBoolean()).isFalse(); // nothing left to return
        assertThat(after.get("returns")).hasSize(2);
    }

    @Test
    void aPayOnDeliveryReturnIsSettledBySellerDirectlyButStillAdjustsTheLedger() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Tin Lantern", "60.00", 5);
        Account buyer = customer();
        JsonNode delivered = deliveredOrder(seller, buyer, pid, 1, null);
        long orderId = id(delivered);
        assertThat(balance(seller)).isEqualByComparingTo("54.00"); // free delivery over 50: 60.00 - 6.00 commission

        JsonNode ret = requestReturn(buyer, orderId, itemId(delivered), 1, 201);
        act(seller, ret.get("id").asLong(), "approve", null, 200);
        JsonNode refunded = act(seller, ret.get("id").asLong(), "refund", null, 200);

        assertThat(refunded.get("refundAmount").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(refunded.get("paymentMethod").asText()).isEqualTo("PAY_ON_DELIVERY");
        assertThat(balance(seller)).isEqualByComparingTo("0.00");
        JsonNode detail = order(buyer, orderId);
        assertThat(detail.get("timeline").get(detail.get("timeline").size() - 1).get("note").asText()).endsWith("refunded by the seller");
        SentEmail email = emailsTo(buyer.email()).stream().filter(x -> x.getKind().equals("RETURN_REFUNDED")).findFirst().orElseThrow();
        assertThat(email.getBody()).contains("£60.00").contains("will refund you directly").doesNotContain("to your card");
    }

    @Test
    void aSellerCanRefundLessThanTheGoodsAndCommissionIsReturnedOnlyOnWhatWasRefunded() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Wall Clock", "60.00", 5);
        Account buyer = customer();
        JsonNode delivered = deliveredOrder(seller, buyer, pid, 1, null);
        JsonNode ret = requestReturn(buyer, id(delivered), itemId(delivered), 1, 201);
        act(seller, ret.get("id").asLong(), "approve", null, 200);

        act(seller, ret.get("id").asLong(), "refund", Map.of("amount", "60.01"), 400); // more than the goods
        act(seller, ret.get("id").asLong(), "refund", Map.of("amount", "0"), 400);
        JsonNode refunded = act(seller, ret.get("id").asLong(), "refund", Map.of("amount", "20.00", "restock", false, "note", "Kept it, small scratch"), 200);

        assertThat(refunded.get("refundAmount").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(refunded.get("restocked").asBoolean()).isFalse();
        assertThat(stock(pid)).isEqualTo(4); // not put back on sale
        assertThat(balance(seller)).isEqualByComparingTo("36.00"); // 54.00 - 20.00 + 2.00 (10% of what was refunded)
    }

    @Test
    void aRefundHappensExactlyOnceEvenWhenTwoPeopleClickAtTheSameTime() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Cast Iron Pan", "40.00", 5);
        Account buyer = customer();
        addToCart(buyer, pid, 1);
        JsonNode res = checkout(buyer, "CARD", 201);
        String ref = res.get("checkoutRef").asText();
        long orderId = id(res.get("orders").get(0));
        send(post("/api/payments/" + ref + "/simulate"), buyer.token(), Map.of("outcome", "PAID"), 200);
        for (String s : List.of("PROCESSING", "SHIPPED", "DELIVERED")) setStatus(seller, orderId, s, null, null);
        JsonNode ret = requestReturn(buyer, orderId, itemId(order(buyer, orderId)), 1, 201);
        long returnId = ret.get("id").asLong();
        act(seller, returnId, "approve", null, 200);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Callable<Integer>> clicks = List.of(
                () -> { go.await(); return statusOf(post("/api/seller/returns/" + returnId + "/refund"), seller.token()); },
                () -> { go.await(); return statusOf(post("/api/admin/returns/" + returnId + "/refund"), admin()); });
        List<Future<Integer>> results = new ArrayList<>();
        for (Callable<Integer> c : clicks) results.add(pool.submit(c));
        go.countDown();
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> f : results) codes.add(f.get());
        pool.shutdown();

        assertThat(codes).containsExactlyInAnyOrder(200, 409); // one wins, the other is told it is already done
        assertThat(payment(buyer, ref).get("refundedAmount").decimalValue()).isEqualByComparingTo("43.99"); // 40.00 + 3.99, once
        assertThat(ledgerTypes(seller).stream().filter(t -> t.equals("REFUND")).count()).isEqualTo(1);
        assertThat(stock(pid)).isEqualTo(5); // 5 - 1 sold + 1 restocked, once
    }

    private int statusOf(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, String token) throws Exception {
        return mvc.perform(b.header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
    }

    // ---------- the rules ----------

    @Test
    void onlyDeliveredOrdersInsideTheWindowCanBeReturned() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Rattan Basket", "15.00", 10);
        Account buyer = customer();
        addToCart(buyer, pid, 1);
        JsonNode placed = checkout(buyer, null, 201).get("orders").get(0);
        requestReturn(buyer, id(placed), itemId(order(buyer, id(placed))), 1, 409); // not delivered yet

        JsonNode delivered = deliveredOrder(seller, buyer, pid, 1, null);
        assertThat(delivered.get("returnDeadline").isNull()).isFalse();
        jdbc.update("update orders set return_deadline = ? where id = ?", utcMinutesAgo(60), id(delivered));
        JsonNode late = requestReturn(buyer, id(delivered), itemId(delivered), 1, 409);
        assertThat(late.get("message").asText()).contains("return window");
        assertThat(order(buyer, id(delivered)).get("canReturn").asBoolean()).isFalse();
    }

    @Test
    void youCanOnlyReturnWhatIsLeftAndOnlyFromYourOwnOrder() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Bamboo Tray", "9.00", 10);
        Account buyer = customer();
        JsonNode delivered = deliveredOrder(seller, buyer, pid, 3, null);
        long orderId = id(delivered);
        long item = itemId(delivered);

        requestReturn(buyer, orderId, item, 4, 409); // more than bought
        send(post("/api/orders/" + orderId + "/returns"), buyer.token(), Map.of("items", List.of(), "reason", "DAMAGED"), 400);
        send(post("/api/orders/" + orderId + "/returns"), buyer.token(),
                Map.of("items", List.of(Map.of("orderItemId", 999999, "quantity", 1)), "reason", "DAMAGED"), 400);
        requestReturn(customer(), orderId, item, 1, 404); // someone else's order
        JsonNode two = requestReturn(buyer, orderId, item, 2, 201);
        requestReturn(buyer, orderId, item, 2, 409); // only 1 left

        // withdrawing the request frees the units again
        send(post("/api/orders/" + orderId + "/returns/" + two.get("id").asLong() + "/cancel"), buyer.token(), null, 200);
        assertThat(order(buyer, orderId).get("items").get(0).get("returnableQuantity").asInt()).isEqualTo(3);
        JsonNode again = requestReturn(buyer, orderId, item, 3, 201);

        // but not once the seller has answered
        act(seller, again.get("id").asLong(), "approve", null, 200);
        send(post("/api/orders/" + orderId + "/returns/" + again.get("id").asLong() + "/cancel"), buyer.token(), null, 409);
    }

    @Test
    void aDeclinedReturnNeedsAReasonFreesTheUnitsAndCantBeRefunded() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Cork Coasters", "6.00", 10);
        Account buyer = customer();
        JsonNode delivered = deliveredOrder(seller, buyer, pid, 2, null);
        JsonNode ret = requestReturn(buyer, id(delivered), itemId(delivered), 2, 201);
        long returnId = ret.get("id").asLong();

        act(seller, returnId, "reject", null, 400); // a reason is required
        JsonNode rejected = act(seller, returnId, "reject", Map.of("note", "Used items can't be returned"), 200);

        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
        act(seller, returnId, "refund", null, 409);
        act(seller, returnId, "approve", null, 409); // already answered
        assertThat(order(buyer, id(delivered)).get("items").get(0).get("returnableQuantity").asInt()).isEqualTo(2);
        SentEmail email = emailsTo(buyer.email()).stream().filter(x -> x.getKind().equals("RETURN_REJECTED")).findFirst().orElseThrow();
        assertThat(email.getBody()).contains("Used items can't be returned");
        assertThat(ledgerTypes(seller)).doesNotContain("REFUND");
    }

    @Test
    void aReturnCantBeRefundedBeforeItIsApproved() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Felt Slippers", "20.00", 10);
        Account buyer = customer();
        JsonNode delivered = deliveredOrder(seller, buyer, pid, 1, null);
        JsonNode ret = requestReturn(buyer, id(delivered), itemId(delivered), 1, 201);

        act(seller, ret.get("id").asLong(), "refund", null, 409);

        assertThat(ledgerTypes(seller)).doesNotContain("REFUND");
    }

    @Test
    void sellersOnlySeeAndActOnTheirOwnReturnsAndAdminsSeeAll() throws Exception {
        Account a = seller();
        Account b = seller();
        long pa = listProduct(a, "Alpha Jar", "10.00", 10);
        Account buyer = customer();
        JsonNode delivered = deliveredOrder(a, buyer, pa, 1, null);
        JsonNode ret = requestReturn(buyer, id(delivered), itemId(delivered), 1, 201);
        long returnId = ret.get("id").asLong();

        act(b, returnId, "approve", null, 404); // not their order
        act(b, returnId, "refund", null, 404);
        assertThat(send(get("/api/seller/returns"), b.token(), null, 200).get("totalItems").asLong()).isZero();
        assertThat(send(get("/api/seller/returns"), a.token(), null, 200).get("totalItems").asLong()).isEqualTo(1);
        assertThat(send(get("/api/seller/stats"), a.token(), null, 200).get("openReturns").asLong()).isEqualTo(1);
        send(post("/api/seller/returns/" + returnId + "/approve"), buyer.token(), null, 403); // customers can't
        assertThat(send(get("/api/admin/returns"), admin(), null, 200).get("totalItems").asLong()).isGreaterThanOrEqualTo(1);

        act(a, returnId, "approve", null, 200);
        assertThat(send(get("/api/seller/stats"), a.token(), null, 200).get("openReturns").asLong()).isZero();
    }

    @Test
    void anAdminCanHandleAReturnOnAnOrderSoldByPacificItself() throws Exception {
        Product house = products.saveAndFlush(new Product("House Kettle", "desc", new BigDecimal("35.00"), 5, null, null));
        Account buyer = customer();
        addToCart(buyer, house.getId(), 1);
        long orderId = id(checkout(buyer, null, 201).get("orders").get(0));
        String admin = admin();
        for (String s : List.of("PROCESSING", "SHIPPED", "DELIVERED")) {
            send(patch("/api/admin/orders/" + orderId + "/status"), admin, Map.of("status", s), 200);
        }
        JsonNode ret = requestReturn(buyer, orderId, itemId(order(buyer, orderId)), 1, 201);
        long returnId = ret.get("id").asLong();

        send(post("/api/admin/returns/" + returnId + "/approve"), admin, Map.of("note", "Send it to our warehouse"), 200);
        JsonNode refunded = send(post("/api/admin/returns/" + returnId + "/refund"), admin, null, 200);

        assertThat(refunded.get("refundAmount").decimalValue()).isEqualByComparingTo("38.99"); // 35.00 + 3.99: the whole order came back
        assertThat(refunded.get("sellerName").asText()).isEqualTo("Pacific");
        assertThat(products.findById(house.getId()).orElseThrow().getStock()).isEqualTo(5); // 5 - 1 + 1
    }

    // ---------- emails ----------

    @Test
    void everyStepOfAReturnTellsTheRightPerson() throws Exception {
        Account seller = seller();
        long pid = listProduct(seller, "Pewter Tankard", "25.00", 10);
        Account buyer = customer();
        addToCart(buyer, pid, 1);
        JsonNode res = checkout(buyer, "CARD", 201);
        long orderId = id(res.get("orders").get(0));
        send(post("/api/payments/" + res.get("checkoutRef").asText() + "/simulate"), buyer.token(), Map.of("outcome", "PAID"), 200);
        for (String s : List.of("PROCESSING", "SHIPPED", "DELIVERED")) setStatus(seller, orderId, s, null, null);
        assertThat(emailsTo(buyer.email()).stream().filter(x -> x.getKind().equals("ORDER_DELIVERED")).findFirst().orElseThrow()
                .getBody()).contains("ask for a return from the order page until"); // the customer is told the deadline

        JsonNode ret = requestReturn(buyer, orderId, itemId(order(buyer, orderId)), 1, 201);
        long returnId = ret.get("id").asLong();
        assertThat(emailsTo(buyer.email()).stream().anyMatch(x -> x.getKind().equals("RETURN_REQUESTED"))).isTrue();
        SentEmail toSeller = emailsTo(seller.email()).stream().filter(x -> x.getKind().equals("SELLER_RETURN_REQUEST")).findFirst().orElseThrow();
        assertThat(toSeller.getBody()).contains("The handle snapped").contains("Pewter Tankard").doesNotContain(buyer.email());

        act(seller, returnId, "approve", Map.of("note", "Post to 9 Depot Road"), 200);
        assertThat(emailsTo(buyer.email()).stream().filter(x -> x.getKind().equals("RETURN_APPROVED")).findFirst().orElseThrow()
                .getBody()).contains("Post to 9 Depot Road");

        act(seller, returnId, "refund", null, 200);
        assertThat(emailsTo(buyer.email()).stream().filter(x -> x.getKind().equals("RETURN_REFUNDED")).findFirst().orElseThrow()
                .getBody()).contains("£28.99").contains("refunded to your card");
    }
}
