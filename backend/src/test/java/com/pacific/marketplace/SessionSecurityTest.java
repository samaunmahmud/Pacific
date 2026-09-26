package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Signing out other devices, and the password rules at sign-up and when changing a password. */
class SessionSecurityTest extends CommittedFlowTestBase {

    @Test
    void signingOutOtherDevicesEndsTheirSessionsButNotThisOne() throws Exception {
        Account phone = customer();
        String laptop = send(post("/api/auth/login"), null,
                Map.of("identifier", phone.email(), "password", "correct-horse-battery"), 200).get("token").asText();
        send(get("/api/me/addresses"), laptop, null, 200);

        JsonNode fresh = send(post("/api/me/sessions/sign-out-others"), phone.token(), null, 200);
        String kept = fresh.get("token").asText();

        send(get("/api/me/addresses"), laptop, null, 401);      // the other device is signed out
        send(get("/api/me/addresses"), phone.token(), null, 401); // so is the old token of this one
        send(get("/api/me/addresses"), kept, null, 200);          // but the fresh token works
        send(post("/api/me/sessions/sign-out-others"), null, null, 401);
    }

    @Test
    void easyPasswordsAreRefusedAtSignUpAndWhenChanging() throws Exception {
        String email = "pat-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        JsonNode refused = send(post("/api/auth/register"), null,
                Map.of("name", "Pat Lee", "email", email, "password", "Password123"), 400);
        assertThat(refused.get("message").asText()).contains("too easy to guess");

        Account c = customer();
        JsonNode change = send(post("/api/me/password"), c.token(),
                Map.of("currentPassword", "correct-horse-battery", "newPassword", "qwerty123!"), 400);
        assertThat(change.get("message").asText()).contains("too easy to guess");
        send(get("/api/me/addresses"), c.token(), null, 200); // a refused change doesn't sign anyone out
    }
}
