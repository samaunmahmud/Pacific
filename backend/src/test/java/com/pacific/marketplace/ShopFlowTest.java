package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacific.marketplace.domain.Category;
import com.pacific.marketplace.domain.Product;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShopFlowTest extends IntegrationTest {

    private void addToCart(String token, Product p, int qty, int expectedStatus) throws Exception {
        mvc.perform(bearer(post("/api/cart/items"), token).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("productId", p.getId(), "quantity", qty)))).andExpect(status().is(expectedStatus));
    }

    private JsonNode checkout(String token) throws Exception {
        MvcResult r = mvc.perform(bearer(post("/api/orders"), token).contentType(MediaType.APPLICATION_JSON)
                .content(body(address()))).andExpect(status().isCreated()).andReturn();
        return read(r).get("orders").get(0); // one seller (Pacific) = one order
    }

    @Test
    void catalogSearchFilterSortAndHidesInactive() throws Exception {
        Category audio = category("Audio", "audio");
        productRepo.saveAndFlush(new Product("Zeta Speaker", "loud", new java.math.BigDecimal("30.00"), 5, null, audio));
        productRepo.saveAndFlush(new Product("Alpha Speaker", "quiet", new java.math.BigDecimal("10.00"), 5, null, audio));
        Product hidden = product("Hidden Speaker", "5.00", 5);
        hidden.setActive(false);
        productRepo.saveAndFlush(hidden);

        mvc.perform(get("/api/products").param("q", "speaker").param("sort", "price_asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Alpha Speaker"))
                .andExpect(jsonPath("$.items[1].name").value("Zeta Speaker"));
        mvc.perform(get("/api/products").param("category", "audio")).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get("/api/products").param("category", "nope")).andExpect(jsonPath("$.totalItems").value(0));
        // a search containing LIKE wildcards is treated literally
        mvc.perform(get("/api/products").param("q", "%")).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/products/" + hidden.getId())).andExpect(status().isNotFound());
    }

    @Test
    void cartMergesQuantitiesAndRespectsStock() throws Exception {
        String token = registerCustomer();
        Product p = product("Gaming Mouse", "25.00", 3);

        addToCart(token, p, 2, 200);
        addToCart(token, p, 1, 200); // merges into one line of 3
        addToCart(token, p, 1, 409); // only 3 in stock

        mvc.perform(bearer(get("/api/cart"), token))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.subtotal").value(75.00))
                .andExpect(jsonPath("$.shipping").value(0.00)); // free shipping over £50

        mvc.perform(bearer(patch("/api/cart/items/" + p.getId()), token).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("quantity", 1)))).andExpect(jsonPath("$.subtotal").value(25.00))
                .andExpect(jsonPath("$.shipping").value(3.99)).andExpect(jsonPath("$.total").value(28.99));
        mvc.perform(bearer(delete("/api/cart/items/" + p.getId()), token)).andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    void checkoutTakesStockClearsCartAndSnapshotsPrices() throws Exception {
        String token = registerCustomer();
        Product p = product("USB-C Hub", "30.00", 5);
        addToCart(token, p, 2, 200);

        JsonNode order = checkout(token);
        assertThat(order.get("status").asText()).isEqualTo("PLACED");
        assertThat(order.get("subtotal").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(order.get("shipping").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(order.get("items").get(0).get("productName").asText()).isEqualTo("USB-C Hub");

        assertThat(stockOf(p)).isEqualTo(3);
        mvc.perform(bearer(get("/api/cart"), token)).andExpect(jsonPath("$.itemCount").value(0));
        mvc.perform(bearer(get("/api/orders"), token)).andExpect(jsonPath("$.length()").value(1));
        // empty cart can't be checked out
        mvc.perform(bearer(post("/api/orders"), token).contentType(MediaType.APPLICATION_JSON)
                .content(body(address()))).andExpect(status().isBadRequest());
    }

    @Test
    void customersOnlySeeTheirOwnOrders() throws Exception {
        String alice = registerCustomer();
        String bob = registerCustomer();
        Product p = product("Item", "10.00", 5);
        addToCart(alice, p, 1, 200);
        long orderId = checkout(alice).get("id").asLong();

        mvc.perform(bearer(get("/api/orders/" + orderId), alice)).andExpect(status().isOk());
        mvc.perform(bearer(get("/api/orders/" + orderId), bob)).andExpect(status().isNotFound());
        mvc.perform(bearer(post("/api/orders/" + orderId + "/cancel"), bob)).andExpect(status().isNotFound());
    }

    @Test
    void cancellingRestocksOnceAndOnlyWhileStillPlaced() throws Exception {
        String token = registerCustomer();
        String admin = adminToken();
        Product p = product("Item", "10.00", 5);
        addToCart(token, p, 2, 200);
        long orderId = checkout(token).get("id").asLong();

        mvc.perform(bearer(post("/api/orders/" + orderId + "/cancel"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(stockOf(p)).isEqualTo(5);
        // cancelling again must not restock a second time
        mvc.perform(bearer(post("/api/orders/" + orderId + "/cancel"), token)).andExpect(status().isConflict());
        mvc.perform(bearer(patch("/api/admin/orders/" + orderId + "/status"), admin)
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "CANCELLED"))))
                .andExpect(status().isConflict());
        assertThat(stockOf(p)).isEqualTo(5);

        // once processing has started, the customer can no longer cancel
        addToCart(token, p, 1, 200);
        long second = checkout(token).get("id").asLong();
        mvc.perform(bearer(patch("/api/admin/orders/" + second + "/status"), admin)
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "PROCESSING"))))
                .andExpect(status().isOk());
        mvc.perform(bearer(post("/api/orders/" + second + "/cancel"), token)).andExpect(status().isConflict());
    }

    @Test
    void adminMovesOrdersThroughValidStatusesOnly() throws Exception {
        String token = registerCustomer();
        String admin = adminToken();
        Product p = product("Item", "10.00", 5);
        addToCart(token, p, 1, 200);
        long id = checkout(token).get("id").asLong();

        // can't skip straight to DELIVERED
        mvc.perform(bearer(patch("/api/admin/orders/" + id + "/status"), admin)
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", "DELIVERED"))))
                .andExpect(status().isConflict());
        for (String next : new String[]{"PROCESSING", "SHIPPED", "DELIVERED"}) {
            mvc.perform(bearer(patch("/api/admin/orders/" + id + "/status"), admin)
                    .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("status", next))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(next));
        }
        mvc.perform(bearer(get("/api/admin/orders").param("status", "DELIVERED"), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void adminManagesProductsAndCategories() throws Exception {
        String admin = adminToken();
        MvcResult cat = mvc.perform(bearer(post("/api/admin/categories"), admin).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "Home & Garden")))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("home-and-garden")).andReturn();
        long catId = read(cat).get("id").asLong();
        mvc.perform(bearer(post("/api/admin/categories"), admin).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "home & garden")))).andExpect(status().isConflict());

        MvcResult created = mvc.perform(bearer(post("/api/admin/products"), admin).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "Garden Lamp", "description", "Bright", "price", "19.99",
                        "stock", 7, "categoryId", catId)))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.category.name").value("Home & Garden")).andReturn();
        long id = read(created).get("id").asLong();

        mvc.perform(bearer(patch("/api/admin/products/" + id + "/stock"), admin).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("stock", 2)))).andExpect(jsonPath("$.stock").value(2));
        mvc.perform(bearer(post("/api/admin/products"), admin).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "Bad", "price", "-1", "stock", 1)))).andExpect(status().isBadRequest());
        mvc.perform(bearer(post("/api/admin/products"), admin).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "Bad", "price", "1.00", "stock", 1, "imageUrl", "javascript:alert(1)"))))
                .andExpect(status().isBadRequest());

        // "delete" hides the product from the storefront but keeps it for the admin
        mvc.perform(bearer(delete("/api/admin/products/" + id), admin)).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());
        mvc.perform(bearer(get("/api/admin/products").param("q", "Garden Lamp"), admin))
                .andExpect(jsonPath("$.totalItems").value(1));
    }
}
