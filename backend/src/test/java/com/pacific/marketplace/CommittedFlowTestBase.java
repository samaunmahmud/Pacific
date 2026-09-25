package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacific.marketplace.domain.SentEmail;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.SentEmailRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for tests that need real commits (emails go out only after commit; some behaviour is about concurrent requests).
 * Not transactional, on its own in-memory database; every test uses fresh accounts, so nothing needs cleaning up.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:lifecycle;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
        + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class CommittedFlowTestBase {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected ProductRepository products;
    @Autowired protected SentEmailRepository sentEmails;
    @Autowired protected JdbcTemplate jdbc;

    protected JsonNode send(MockHttpServletRequestBuilder b, String token, Object body, int expected) throws Exception {
        if (token != null) b = b.header("Authorization", "Bearer " + token);
        if (body != null) b = b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String text = mvc.perform(b).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return text.isEmpty() ? null : json.readTree(text);
    }

    protected record Account(String email, String token) {
    }

    protected Account customer() throws Exception {
        String email = "c-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        JsonNode res = send(post("/api/auth/register"), null,
                Map.of("name", "Casey Customer", "email", email, "password", "correct-horse-battery"), 201);
        send(post("/api/admin/customers/verify-email"), admin(), Map.of("email", email), 200);
        return new Account(email, res.get("token").asText());
    }

    protected String admin() throws Exception {
        return send(post("/api/auth/admin/login"), null,
                Map.of("identifier", "testadmin", "password", "testadmin-password"), 200).get("token").asText();
    }

    protected Account seller() throws Exception {
        Account a = customer();
        JsonNode store = send(post("/api/seller/apply"), a.token(),
                Map.of("storeName", "Store " + UUID.randomUUID().toString().substring(0, 6), "description", "Things"), 201);
        send(patch("/api/admin/sellers/" + store.get("id").asLong() + "/status"), admin(), Map.of("status", "APPROVED"), 200);
        return a;
    }

    protected long listProduct(Account seller, String name, String price, int stock) throws Exception {
        return send(post("/api/seller/products"), seller.token(),
                Map.of("name", name, "price", price, "stock", stock), 201).get("id").asLong();
    }

    protected void addToCart(Account c, long productId, int qty) throws Exception {
        send(post("/api/cart/items"), c.token(), Map.of("productId", productId, "quantity", qty), 200);
    }

    protected JsonNode checkout(Account c, String method, int expected) throws Exception {
        return checkout(c, method, "Casey Customer", expected);
    }

    protected JsonNode checkout(Account c, String method, String recipient, int expected) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>(Map.of("name", recipient, "line1", "1 High Street", "city", "Uxbridge",
                "postcode", "UB8 1AA", "country", "United Kingdom"));
        if (method != null) req.put("paymentMethod", method);
        return send(post("/api/orders"), c.token(), req, expected);
    }

    protected JsonNode setStatus(Account seller, long orderId, String status, String carrier, String number) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("status", status));
        if (carrier != null) body.put("carrier", carrier);
        if (number != null) body.put("trackingNumber", number);
        return send(patch("/api/seller/orders/" + orderId + "/status"), seller.token(), body, 200);
    }

    protected JsonNode order(Account c, long id) throws Exception {
        return send(get("/api/orders/" + id), c.token(), null, 200);
    }

    /** Emails sent to this address, apart from the "confirm your email" one every new customer gets at sign-up. */
    protected List<SentEmail> emailsTo(String address) {
        return sentEmails.findAll().stream()
                .filter(e -> e.getToAddress().equals(address) && !e.getKind().equals("VERIFY_EMAIL")).toList();
    }

    protected static List<String> timeline(JsonNode order) {
        List<String> types = new ArrayList<>();
        order.get("timeline").forEach(e -> types.add(e.get("type").asText()));
        return types;
    }

    protected static long id(JsonNode order) {
        return order.get("id").asLong();
    }


    /**
     * A moment in the past as the database stores it: the app keeps times as UTC clock values, so hand-written SQL must
     * not use the database's own (local time zone) CURRENT_TIMESTAMP.
     */
    protected static java.time.LocalDateTime utcMinutesAgo(long minutes) {
        return java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(minutes);
    }

    /** A delivered order for the buyer: pays (card orders through the simulator) and the seller ships and delivers it. */
    protected JsonNode deliveredOrder(Account seller, Account buyer, long productId, int qty, String method) throws Exception {
        addToCart(buyer, productId, qty);
        JsonNode res = checkout(buyer, method, 201);
        long orderId = id(res.get("orders").get(0));
        if ("CARD".equals(method)) {
            send(post("/api/payments/" + res.get("checkoutRef").asText() + "/simulate"), buyer.token(), Map.of("outcome", "PAID"), 200);
        }
        setStatus(seller, orderId, "PROCESSING", null, null);
        setStatus(seller, orderId, "SHIPPED", null, null);
        setStatus(seller, orderId, "DELIVERED", null, null);
        return order(buyer, orderId);
    }
}
