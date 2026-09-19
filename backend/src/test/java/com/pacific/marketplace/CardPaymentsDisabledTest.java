package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.repo.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/** With no provider configured, the shop is pay-on-delivery only and card checkouts fail before touching anything. */
@TestPropertySource(properties = "app.payments.simulator-enabled=false")
class CardPaymentsDisabledTest extends PaymentTestBase {

    @Autowired PaymentRepository paymentRepo;

    @Test
    void cardCheckoutIsRefusedAndNothingIsReserved() throws Exception {
        String token = registerCustomer();
        Product p = product("Scooter", "120.00", 2);
        addToCart(token, p.getId(), 1);

        mvc.perform(bearer(get("/api/payments/config"), token)).andExpect(jsonPath("$.cardEnabled").value(false));
        JsonNode error = send(post("/api/orders"), token, java.util.Map.of("name", "A", "line1", "1 Road", "city", "X",
                "postcode", "AB1 2CD", "country", "UK", "paymentMethod", "CARD"), 400);

        assertThat(error.get("message").asText()).contains("pay on delivery");
        assertThat(stockOf(p)).isEqualTo(2);
        assertThat(paymentRepo.count()).isZero();
        mvc.perform(bearer(get("/api/cart"), token)).andExpect(jsonPath("$.itemCount").value(1)); // cart untouched
    }

    @Test
    void payOnDeliveryStillWorks() throws Exception {
        String token = registerCustomer();
        Product p = product("Helmet", "60.00", 2);
        addToCart(token, p.getId(), 1);

        JsonNode res = send(post("/api/orders"), token, address(), 201);
        assertThat(res.get("orders").get(0).get("status").asText()).isEqualTo("PLACED");
    }
}
