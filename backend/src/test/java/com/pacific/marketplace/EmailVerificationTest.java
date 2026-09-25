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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * New customers confirm their email before ordering, selling or posting questions. Committed (not rolled back):
 * the confirmation email only goes out after sign-up's transaction commits.
 */
class EmailVerificationTest extends CommittedFlowTestBase {

    @MockitoSpyBean Mailer mailer;

    private Account signUp() throws Exception {
        String email = "v-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        JsonNode res = send(post("/api/auth/register"), null,
                Map.of("name", "Vera Verify", "email", email, "password", "correct-horse-battery"), 201);
        assertThat(res.get("user").get("emailVerified").asBoolean()).isFalse();
        return new Account(email, res.get("token").asText());
    }

    /** The raw token from the latest confirmation email sent to this address. */
    private String linkSentTo(String email) throws Exception {
        ArgumentCaptor<Email> sent = ArgumentCaptor.forClass(Email.class);
        verify(mailer, atLeast(0)).send(sent.capture());
        List<Email> mine = sent.getAllValues().stream().filter(e -> e.to().equals(email) && e.kind().equals("VERIFY_EMAIL")).toList();
        assertThat(mine).isNotEmpty();
        Matcher m = Pattern.compile("verify-email\\?token=([A-Za-z0-9_-]+)").matcher(mine.get(mine.size() - 1).text());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private JsonNode confirm(String token, int expected) throws Exception {
        return send(post("/api/auth/verify-email"), null, Map.of("token", token), expected);
    }

    private boolean verified(Account a) throws Exception {
        return send(get("/api/auth/me"), a.token(), null, 200).get("emailVerified").asBoolean();
    }

    @Test
    void anUnconfirmedCustomerCanShopButNotOrderSellOrAskUntilTheyOpenTheLink() throws Exception {
        Account seller = seller();
        long product = listProduct(seller, "Verified kettle", "20.00", 5);
        Account vera = signUp();

        addToCart(vera, product, 1); // browsing and the cart work straight away
        JsonNode refused = checkout(vera, null, 403);
        assertThat(refused.get("message").asText()).contains("confirm your email").contains(vera.email());
        send(post("/api/seller/apply"), vera.token(), Map.of("storeName", "Vera's " + UUID.randomUUID(), "description", "x"), 403);
        send(post("/api/products/" + product + "/questions"), vera.token(), Map.of("text", "Is it quiet?"), 403);

        // The link works without signing in (people open it on their phone).
        JsonNode user = confirm(linkSentTo(vera.email()), 200);
        assertThat(user.get("emailVerified").asBoolean()).isTrue();
        assertThat(verified(vera)).isTrue();

        checkout(vera, null, 201);
        send(post("/api/products/" + product + "/questions"), vera.token(), Map.of("text", "Is it quiet?"), 201);
    }

    @Test
    void theEmailLogHidesTheLinkAndALinkCanBeOpenedAgainButNotForged() throws Exception {
        Account vera = signUp();
        String token = linkSentTo(vera.email());

        List<SentEmail> logged = sentEmails.findAll().stream()
                .filter(e -> e.getToAddress().equals(vera.email()) && e.getKind().equals("VERIFY_EMAIL")).toList();
        assertThat(logged).hasSize(1);
        assertThat(logged.get(0).getBody()).contains("[confirmation link hidden]").doesNotContain(token);

        confirm(token, 200);
        assertThat(confirm(token, 200).get("emailVerified").asBoolean()).isTrue(); // a second click is harmless
        confirm("not-a-real-token", 400);
    }

    @Test
    void anExpiredLinkIsRefusedAndOnlyTheNewestLinkWorks() throws Exception {
        Account vera = signUp();
        String first = linkSentTo(vera.email());
        jdbc.update("update email_verification_tokens set expires_at = ?, created_at = ? where used_at is null and user_id = "
                + "(select id from users where email = ?)", utcMinutesAgo(1), utcMinutesAgo(60), vera.email());
        confirm(first, 400);
        assertThat(verified(vera)).isFalse();

        send(post("/api/auth/verify-email/resend"), vera.token(), null, 202);
        String second = linkSentTo(vera.email());
        assertThat(second).isNotEqualTo(first);
        // Asking again straight away is refused rather than flooding the inbox.
        send(post("/api/auth/verify-email/resend"), vera.token(), null, 429);

        confirm(second, 200);
        assertThat(verified(vera)).isTrue();
        // Once confirmed, "send it again" quietly does nothing.
        long before = sentEmails.count();
        send(post("/api/auth/verify-email/resend"), vera.token(), null, 202);
        assertThat(sentEmails.count()).isEqualTo(before);
    }

    @Test
    void anAdminCanConfirmACustomerByHandButOnlyAdminsCan() throws Exception {
        Account vera = signUp();
        Account other = customer();

        send(post("/api/admin/customers/verify-email"), other.token(), Map.of("email", vera.email()), 403);
        send(post("/api/admin/customers/verify-email"), admin(), Map.of("email", "nobody-" + UUID.randomUUID() + "@example.com"), 404);
        JsonNode user = send(post("/api/admin/customers/verify-email"), admin(), Map.of("email", vera.email().toUpperCase()), 200);
        assertThat(user.get("emailVerified").asBoolean()).isTrue();
        assertThat(verified(vera)).isTrue();

        // The link sent at sign-up stops working once the address is confirmed another way, and says so harmlessly.
        assertThat(confirm(linkSentTo(vera.email()), 200).get("emailVerified").asBoolean()).isTrue();
    }

    @Test
    void adminsAreNeverAskedToConfirm() throws Exception {
        JsonNode me = send(get("/api/auth/me"), admin(), null, 200);
        assertThat(me.get("emailVerified").asBoolean()).isTrue();
    }
}
