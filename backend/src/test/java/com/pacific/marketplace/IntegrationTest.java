package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacific.marketplace.domain.Category;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.repo.CategoryRepository;
import com.pacific.marketplace.repo.ProductRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Each test runs in a transaction that is rolled back, so tests don't see each other's data. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class IntegrationTest {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected ProductRepository productRepo;
    @Autowired protected CategoryRepository categoryRepo;
    @Autowired protected EntityManager em;

    protected static final String PASSWORD = "correct-horse-battery";

    protected String uniqueEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    protected String body(Map<String, ?> map) throws Exception {
        return json.writeValueAsString(map);
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    /** Registers a customer and returns its bearer token. */
    protected String registerCustomer() throws Exception {
        return registerCustomer(uniqueEmail());
    }

    /** Registers a customer and confirms their email, as most tests need a customer who can order and sell. */
    protected String registerCustomer(String email) throws Exception {
        String token = registerUnconfirmed(email);
        confirmEmail(email);
        return token;
    }

    protected String registerUnconfirmed(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "Test Customer", "email", email, "password", PASSWORD)))).andReturn();
        return read(r).get("token").asText();
    }

    protected void confirmEmail(String email) throws Exception {
        mvc.perform(bearer(post("/api/admin/customers/verify-email"), adminToken()).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email)))).andExpect(status().isOk());
    }

    protected String adminToken() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("identifier", "testadmin", "password", "testadmin-password")))).andReturn();
        return read(r).get("token").asText();
    }

    protected Product product(String name, String price, int stock) {
        return productRepo.saveAndFlush(new Product(name, "desc", new BigDecimal(price), stock, null, null));
    }

    /**
     * Reads the stock straight from the database. Stock changes are atomic UPDATE statements, so the entity
     * cached in the test's persistence context would be stale; clearing it forces a fresh read.
     */
    protected int stockOf(Product p) {
        em.flush();
        em.clear();
        return productRepo.findById(p.getId()).orElseThrow().getStock();
    }

    protected Category category(String name, String slug) {
        return categoryRepo.saveAndFlush(new Category(name, slug));
    }

    protected static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token);
    }

    protected Map<String, Object> address() {
        return Map.of("name", "Test Customer", "line1", "1 High Street", "city", "Uxbridge",
                "postcode", "UB8 1AA", "country", "United Kingdom");
    }
}
