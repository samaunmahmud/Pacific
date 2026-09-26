package com.pacific.marketplace;

import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every API response carries the security headers, whether it succeeds or not. */
class SecurityHeadersTest extends IntegrationTest {

    @Test
    void responsesCantRunScriptsBeFramedOrLeakTheirAddress() throws Exception {
        for (String path : new String[] {"/api/categories", "/api/orders", "/api/no-such-thing"}) {
            mvc.perform(get(path))
                    .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                    .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()"));
        }
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }
}
