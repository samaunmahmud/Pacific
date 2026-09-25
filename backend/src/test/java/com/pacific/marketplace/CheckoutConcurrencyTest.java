package com.pacific.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.repo.OrderRepository;
import com.pacific.marketplace.repo.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Not transactional: these tests need real commits and rollbacks, so they use their own in-memory database
 * (which is why nothing needs cleaning up).
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
        + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckoutConcurrencyTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProductRepository products;
    @Autowired OrderRepository orders;
    @Autowired TransactionTemplate tx;

    private static final Map<String, Object> ADDRESS = Map.of("name", "T", "line1", "1 High St", "city", "Uxbridge",
            "postcode", "UB8 1AA", "country", "UK");

    private String customer() throws Exception {
        String email = "c-" + UUID.randomUUID() + "@example.com";
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "C", "email", email, "password", "password-123"))))
                .andReturn().getResponse().getContentAsString();
        String admin = json.readTree(mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("identifier", "testadmin", "password", "testadmin-password"))))
                .andReturn().getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/admin/customers/verify-email").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("email", email))));
        return json.readTree(res).get("token").asText();
    }

    private void addToCart(String token, Product p, int qty) throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("productId", p.getId(), "quantity", qty))));
    }

    private int checkout(String token) throws Exception {
        return mvc.perform(post("/api/orders").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(ADDRESS)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void failedCheckoutRollsBackEverythingIncludingStockAlreadyTaken() throws Exception {
        String token = customer();
        Product a = products.save(new Product("Item A", "d", new BigDecimal("10.00"), 5, null, null));
        Product b = products.save(new Product("Item B", "d", new BigDecimal("10.00"), 5, null, null));
        addToCart(token, a, 2);
        addToCart(token, b, 4);
        long ordersBefore = orders.count();

        tx.executeWithoutResult(s -> products.decrementStock(b.getId(), 3)); // someone else buys most of B in the meantime

        assertThat(checkout(token)).isEqualTo(409);
        // A was processed before B failed; its stock must have been given back by the rollback
        assertThat(products.findById(a.getId()).orElseThrow().getStock()).isEqualTo(5);
        assertThat(products.findById(b.getId()).orElseThrow().getStock()).isEqualTo(2);
        assertThat(orders.count()).isEqualTo(ordersBefore);
    }

    @Test
    void concurrentCheckoutsNeverOversellTheLastUnits() throws Exception {
        int stock = 3;
        int buyers = 8;
        Product p = products.save(new Product("Last Units", "d", new BigDecimal("10.00"), stock, null, null));
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            String t = customer();
            addToCart(t, p, 1);
            tokens.add(t);
        }

        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (String t : tokens) {
            Callable<Integer> task = () -> {
                go.await();
                return checkout(t);
            };
            results.add(pool.submit(task));
        }
        go.countDown();
        int created = 0;
        for (Future<Integer> f : results) {
            if (f.get() == 201) created++;
        }
        pool.shutdown();

        assertThat(created).isEqualTo(stock);
        assertThat(products.findById(p.getId()).orElseThrow().getStock()).isZero();
    }
}
