package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacific.marketplace.domain.Product;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shared steps for the card-payment tests: shopping, sellers, and the payment calls. */
abstract class PaymentTestBase extends IntegrationTest {

    protected record Seller(String token, long id) {
    }

    protected JsonNode send(MockHttpServletRequestBuilder b, String token, Object body, int expected) throws Exception {
        if (token != null) b = bearer(b, token);
        if (body != null) b = b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        MvcResult r = mvc.perform(b).andExpect(status().is(expected)).andReturn();
        String text = r.getResponse().getContentAsString();
        return text.isEmpty() ? null : json.readTree(text);
    }

    protected void addToCart(String token, long productId, int qty) throws Exception {
        send(post("/api/cart/items"), token, Map.of("productId", productId, "quantity", qty), 200);
    }

    /** How many of the product are in the customer's cart (0 if none). */
    protected int cartQuantity(String token, long productId) throws Exception {
        for (JsonNode line : send(get("/api/cart"), token, null, 200).get("items")) {
            if (line.get("productId").asLong() == productId) return line.get("quantity").asInt();
        }
        return 0;
    }

    /** Checks out the cart paying by card and returns the whole response ({checkoutRef, orders, total, payment}). */
    protected JsonNode cardCheckout(String token) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>(address());
        req.put("paymentMethod", "CARD");
        return send(post("/api/orders"), token, req, 201);
    }

    protected JsonNode payment(String token, String ref, int expected) throws Exception {
        return send(get("/api/payments/" + ref), token, null, expected);
    }

    protected JsonNode simulate(String token, String ref, String outcome, int expected) throws Exception {
        return send(post("/api/payments/" + ref + "/simulate"), token, Map.of("outcome", outcome), expected);
    }

    protected JsonNode cancelPayment(String token, String ref, int expected) throws Exception {
        return send(post("/api/payments/" + ref + "/cancel"), token, null, expected);
    }

    protected JsonNode order(String token, long orderId) throws Exception {
        return send(get("/api/orders/" + orderId), token, null, 200);
    }

    protected Seller approvedSeller(String storeName) throws Exception {
        String token = registerCustomer();
        JsonNode s = send(post("/api/seller/apply"), token,
                Map.of("storeName", storeName, "description", "We sell things"), 201);
        send(patch("/api/admin/sellers/" + s.get("id").asLong() + "/status"), adminToken(),
                Map.of("status", "APPROVED"), 200);
        return new Seller(token, s.get("id").asLong());
    }

    protected Product sellerProduct(Seller seller, String name, String price, int stock) throws Exception {
        long id = send(post("/api/seller/products"), seller.token(),
                Map.of("name", name, "price", price, "stock", stock), 201).get("id").asLong();
        return productRepo.findById(id).orElseThrow();
    }
}
