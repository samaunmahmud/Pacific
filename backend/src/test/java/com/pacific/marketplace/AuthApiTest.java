package com.pacific.marketplace;

import com.pacific.marketplace.domain.User;
import com.pacific.marketplace.repo.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends IntegrationTest {

    @Autowired UserRepository users;

    @Test
    void registerStoresBcryptHashAndReturnsToken() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Ada", "email", email.toUpperCase(), "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.email").value(email)); // normalised to lower case

        User saved = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void registerRejectsDuplicateEmailAndWeakPassword() throws Exception {
        String email = uniqueEmail();
        registerCustomer(email);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Again", "email", email, "password", PASSWORD))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Weak", "email", uniqueEmail(), "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void customerAndAdminMustUseTheirOwnPortal() throws Exception {
        String email = uniqueEmail();
        registerCustomer(email);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("identifier", email, "password", PASSWORD))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("identifier", email, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
        // an admin can't sign in through the customer portal, and a customer can't through the admin portal
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("identifier", "testadmin", "password", "testadmin-password"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("identifier", email, "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("identifier", "testadmin", "password", "testadmin-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void endpointsAreProtectedByRole() throws Exception {
        String customer = registerCustomer();
        String admin = adminToken();

        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/stats")).andExpect(status().isUnauthorized());
        mvc.perform(bearer(get("/api/admin/stats"), customer)).andExpect(status().isForbidden());
        mvc.perform(bearer(get("/api/cart"), admin)).andExpect(status().isForbidden());
        mvc.perform(bearer(get("/api/admin/stats"), admin)).andExpect(status().isOk());
        mvc.perform(get("/api/products")).andExpect(status().isOk()); // storefront is public
        mvc.perform(bearer(get("/api/auth/me"), customer)).andExpect(status().isOk());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = registerCustomer();
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");
        mvc.perform(bearer(get("/api/cart"), tampered)).andExpect(status().isUnauthorized());
    }
}
