package com.pacific.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.pacific.marketplace.domain.SentEmail;
import com.pacific.marketplace.notify.Email;
import com.pacific.marketplace.notify.Mailer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Forgotten and changed passwords, signing other sessions out, guessing limits, and the saved address book.
 * Committed (not rolled back): reset emails only go out after the request's transaction commits.
 */
class AccountSecurityTest extends CommittedFlowTestBase {

    @MockitoSpyBean Mailer mailer;

    private static final String PASSWORD = "correct-horse-battery";

    // ---------- helpers ----------

    private JsonNode login(String email, String password, int expected) throws Exception {
        return send(post("/api/auth/login"), null, Map.of("identifier", email, "password", password), expected);
    }

    private void me(String token, int expected) throws Exception {
        send(get("/api/auth/me"), token, null, expected);
    }

    private JsonNode forgot(String email) throws Exception {
        return send(post("/api/auth/forgot-password"), null, Map.of("email", email), 202);
    }

    /** The raw token from the latest reset email sent to this address. */
    private String tokenSentTo(String email) throws Exception {
        ArgumentCaptor<Email> sent = ArgumentCaptor.forClass(Email.class);
        verify(mailer, atLeast(0)).send(sent.capture());
        List<Email> mine = sent.getAllValues().stream().filter(e -> e.to().equals(email) && e.kind().equals("PASSWORD_RESET")).toList();
        if (mine.isEmpty()) return null;
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(mine.get(mine.size() - 1).text());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private long resetEmailsTo(String email) {
        return emailsTo(email).stream().filter(e -> e.getKind().equals("PASSWORD_RESET")).count();
    }

    private Map<String, Object> address(String name, String line1) {
        return Map.of("name", name, "line1", line1, "city", "Leeds", "postcode", "LS1 1AA", "country", "United Kingdom");
    }

    // ---------- forgot and reset ----------

    @Test
    void askingForAResetSaysTheSameThingForAnyEmailAndOnlyEmailsRealCustomers() throws Exception {
        Account known = customer();
        String stranger = "nobody-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";

        JsonNode a = forgot(known.email());
        JsonNode b = forgot(stranger);

        assertThat(a.get("message").asText()).isEqualTo(b.get("message").asText()); // can't tell who is registered
        assertThat(resetEmailsTo(known.email())).isEqualTo(1);
        assertThat(resetEmailsTo(stranger)).isZero();
        send(post("/api/auth/forgot-password"), null, Map.of("email", "not-an-email"), 400);
    }

    @Test
    void aResetLinkChoosesANewPasswordOnceAndSignsEveryOldSessionOut() throws Exception {
        Account c = customer();
        String oldSession = login(c.email(), PASSWORD, 200).get("token").asText();
        me(oldSession, 200);

        forgot(c.email());
        String link = tokenSentTo(c.email());
        assertThat(link).isNotBlank();
        send(post("/api/auth/reset-password"), null, Map.of("token", link, "password", "a-brand-new-password"), 204);

        login(c.email(), PASSWORD, 401); // the old password is gone
        String fresh = login(c.email(), "a-brand-new-password", 200).get("token").asText();
        me(fresh, 200);
        me(oldSession, 401); // signed out everywhere else, including a stolen token
        me(c.token(), 401);
        send(post("/api/auth/reset-password"), null, Map.of("token", link, "password", "another-new-password"), 400); // single use
        assertThat(emailsTo(c.email()).stream().anyMatch(e -> e.getKind().equals("PASSWORD_CHANGED"))).isTrue();
    }

    @Test
    void bogusExpiredAndWeakResetsAreRefused() throws Exception {
        Account c = customer();
        send(post("/api/auth/reset-password"), null, Map.of("token", "definitely-not-a-real-token", "password", "a-brand-new-password"), 400);

        forgot(c.email());
        String link = tokenSentTo(c.email());
        send(post("/api/auth/reset-password"), null, Map.of("token", link, "password", "short"), 400); // policy still applies
        jdbc.update("update password_reset_tokens set expires_at = ? where user_id = (select id from users where email = ?)",
                utcMinutesAgo(1), c.email());
        JsonNode expired = send(post("/api/auth/reset-password"), null, Map.of("token", link, "password", "a-brand-new-password"), 400);
        assertThat(expired.get("message").asText()).contains("invalid or has expired");
        login(c.email(), PASSWORD, 200); // nothing changed
    }

    @Test
    void onlyTheNewestLinkWorksAndAccountsGetOneEmailAMinute() throws Exception {
        Account c = customer();
        forgot(c.email());
        forgot(c.email()); // straight away: no second email
        assertThat(resetEmailsTo(c.email())).isEqualTo(1);
        String first = tokenSentTo(c.email());

        jdbc.update("update password_reset_tokens set created_at = ? where user_id = (select id from users where email = ?)",
                utcMinutesAgo(5), c.email());
        forgot(c.email());
        assertThat(resetEmailsTo(c.email())).isEqualTo(2);
        String second = tokenSentTo(c.email());

        assertThat(second).isNotEqualTo(first);
        send(post("/api/auth/reset-password"), null, Map.of("token", first, "password", "a-brand-new-password"), 400); // replaced
        send(post("/api/auth/reset-password"), null, Map.of("token", second, "password", "a-brand-new-password"), 204);
    }

    @Test
    void theAdminEmailLogNeverContainsTheResetLinkAndTheDatabaseOnlyHoldsAHash() throws Exception {
        Account c = customer();
        forgot(c.email());
        String link = tokenSentTo(c.email());

        SentEmail recorded = emailsTo(c.email()).stream().filter(e -> e.getKind().equals("PASSWORD_RESET")).findFirst().orElseThrow();
        assertThat(recorded.getBody()).contains("[reset link hidden]").doesNotContain(link).doesNotContain("reset-password?token");
        JsonNode viaApi = send(get("/api/admin/emails"), admin(), null, 200);
        assertThat(viaApi.toString()).doesNotContain(link);
        String stored = jdbc.queryForObject("select token_hash from password_reset_tokens where user_id = (select id from users where email = ?)",
                String.class, c.email());
        assertThat(stored).hasSize(64).isNotEqualTo(link).doesNotContain(link);
    }

    // ---------- changing a password while signed in ----------

    @Test
    void changingYourPasswordChecksTheCurrentOneKeepsThisSessionAndEndsTheOthers() throws Exception {
        Account c = customer();
        String other = login(c.email(), PASSWORD, 200).get("token").asText();

        send(post("/api/me/password"), c.token(), Map.of("currentPassword", "wrong-password", "newPassword", "a-brand-new-password"), 400);
        me(c.token(), 200); // a mistyped password does not sign you out
        send(post("/api/me/password"), c.token(), Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD), 400); // must differ
        send(post("/api/me/password"), c.token(), Map.of("currentPassword", PASSWORD, "newPassword", "short"), 400);

        JsonNode changed = send(post("/api/me/password"), c.token(), Map.of("currentPassword", PASSWORD, "newPassword", "a-brand-new-password"), 200);
        String carriesOn = changed.get("token").asText();
        me(carriesOn, 200);
        me(other, 401);
        me(c.token(), 401);
        login(c.email(), "a-brand-new-password", 200);
        login(c.email(), PASSWORD, 401);
        assertThat(emailsTo(c.email()).stream().anyMatch(e -> e.getKind().equals("PASSWORD_CHANGED"))).isTrue();
    }

    @Test
    void guessingTheCurrentPasswordIsLimited() throws Exception {
        Account c = customer();
        for (int i = 0; i < 5; i++) {
            send(post("/api/me/password"), c.token(), Map.of("currentPassword", "wrong-" + i + "-password", "newPassword", "a-brand-new-password"), 400);
        }
        JsonNode blocked = send(post("/api/me/password"), c.token(), Map.of("currentPassword", PASSWORD, "newPassword", "a-brand-new-password"), 429);
        assertThat(blocked.get("message").asText()).contains("try again in");
    }

    @Test
    void aCustomerCanChangeTheirName() throws Exception {
        Account c = customer();
        JsonNode renamed = send(patch("/api/me"), c.token(), Map.of("name", "  Casey Q. Customer "), 200);
        assertThat(renamed.get("name").asText()).isEqualTo("Casey Q. Customer");
        send(patch("/api/me"), c.token(), Map.of("name", " "), 400);
    }

    // ---------- guessing limits at sign-in ----------

    @Test
    void repeatedWrongPasswordsBlockThatAccountFromThatAddressEvenForTheRightPassword() throws Exception {
        Account victim = customer();
        Account other = customer();
        for (int i = 0; i < 5; i++) login(victim.email(), "wrong-guess-" + i, 401);

        JsonNode blocked = login(victim.email(), PASSWORD, 429);
        assertThat(blocked.get("message").asText()).contains("Too many failed sign-in attempts").contains("try again in");
        login(other.email(), PASSWORD, 200); // other accounts are unaffected
    }

    @Test
    void aSuccessfulSignInStartsTheCountAgain() throws Exception {
        Account c = customer();
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 3; i++) login(c.email(), "wrong-guess-" + i, 401);
            login(c.email(), PASSWORD, 200); // three wrong, then right: never reaches five in a row
        }
    }

    @Test
    void adminSignInIsLimitedToo() throws Exception {
        String id = "no-such-admin-" + UUID.randomUUID().toString().substring(0, 6);
        for (int i = 0; i < 5; i++) {
            send(post("/api/auth/admin/login"), null, Map.of("identifier", id, "password", "wrong-guess-" + i), 401);
        }
        send(post("/api/auth/admin/login"), null, Map.of("identifier", id, "password", "wrong-guess-again"), 429);
    }

    // ---------- the address book ----------

    @Test
    void savedAddressesKeepExactlyOneDefaultAndPromoteAnotherWhenItIsDeleted() throws Exception {
        Account c = customer();
        JsonNode home = send(post("/api/me/addresses"), c.token(), address("Casey", "1 Home Street"), 201);
        assertThat(home.get("isDefault").asBoolean()).isTrue(); // the first one is the default
        JsonNode work = send(post("/api/me/addresses"), c.token(), address("Casey (work)", "2 Work Road"), 201);
        assertThat(work.get("isDefault").asBoolean()).isFalse();

        send(post("/api/me/addresses/" + work.get("id").asLong() + "/default"), c.token(), null, 200);
        JsonNode list = send(get("/api/me/addresses"), c.token(), null, 200);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("id").asLong()).isEqualTo(work.get("id").asLong()); // default first
        assertThat(list.get(0).get("isDefault").asBoolean()).isTrue();
        assertThat(list.get(1).get("isDefault").asBoolean()).isFalse();

        JsonNode edited = send(put("/api/me/addresses/" + home.get("id").asLong()), c.token(),
                Map.of("name", "Casey", "line1", "1 Home Street, Flat 2", "city", "Leeds", "postcode", "LS1 1AA", "country", "United Kingdom", "makeDefault", true), 200);
        assertThat(edited.get("isDefault").asBoolean()).isTrue();
        assertThat(send(get("/api/me/addresses"), c.token(), null, 200).get(0).get("line1").asText()).isEqualTo("1 Home Street, Flat 2");

        send(delete("/api/me/addresses/" + home.get("id").asLong()), c.token(), null, 204); // deleting the default...
        JsonNode left = send(get("/api/me/addresses"), c.token(), null, 200);
        assertThat(left).hasSize(1);
        assertThat(left.get(0).get("isDefault").asBoolean()).isTrue(); // ...promotes the other one
    }

    @Test
    void addressesArePrivateValidatedAndCapped() throws Exception {
        Account c = customer();
        Account intruder = customer();
        long id = send(post("/api/me/addresses"), c.token(), address("Casey", "1 Home Street"), 201).get("id").asLong();

        send(put("/api/me/addresses/" + id), intruder.token(), address("Mallory", "6 Evil Lane"), 404);
        send(delete("/api/me/addresses/" + id), intruder.token(), null, 404);
        send(post("/api/me/addresses/" + id + "/default"), intruder.token(), null, 404);
        assertThat(send(get("/api/me/addresses"), intruder.token(), null, 200)).isEmpty();
        send(post("/api/me/addresses"), c.token(), Map.of("name", "", "line1", "x", "city", "Leeds", "postcode", "LS1", "country", "UK"), 400);
        send(get("/api/me/addresses"), null, null, 401);

        for (int i = 2; i <= 10; i++) send(post("/api/me/addresses"), c.token(), address("Casey", i + " Street"), 201);
        send(post("/api/me/addresses"), c.token(), address("Casey", "11 Street"), 409); // at most 10
    }
}
