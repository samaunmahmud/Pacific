package com.pacific.marketplace;

import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.Review;
import com.pacific.marketplace.repo.ReviewRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewFlowTest extends IntegrationTest {

    @Autowired ReviewRepository reviewRepo;

    private void buy(String token, Product p) throws Exception {
        mvc.perform(bearer(post("/api/cart/items"), token).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("productId", p.getId(), "quantity", 1)))).andExpect(status().isOk());
        mvc.perform(bearer(post("/api/orders"), token).contentType(MediaType.APPLICATION_JSON)
                .content(body(address()))).andExpect(status().isCreated());
    }

    private ResultActions review(String token, Product p, int rating, String comment) throws Exception {
        return mvc.perform(bearer(post("/api/products/" + p.getId() + "/reviews"), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("rating", rating, "title", "Great", "comment", comment))));
    }

    private long reviewId(String token, Product p, int rating, String comment) throws Exception {
        buy(token, p);
        return read(review(token, p, rating, comment).andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    @Test
    void mustHaveBoughtTheProductAndCanReviewOnlyOnce() throws Exception {
        String token = registerCustomer();
        Product p = product("Headphones", "80.00", 5);

        review(token, p, 5, "Never bought it").andExpect(status().isForbidden());
        buy(token, p);
        review(token, p, 4, "Solid").andExpect(status().isCreated()).andExpect(jsonPath("$.rating").value(4));
        review(token, p, 5, "Twice").andExpect(status().isConflict());
        review(token, p, 0, "Bad rating").andExpect(status().isBadRequest());
    }

    @Test
    void cancelledOrdersDoNotCountAsPurchases() throws Exception {
        String token = registerCustomer();
        Product p = product("Headphones", "80.00", 5);
        buy(token, p);
        long orderId = read(mvc.perform(bearer(get("/api/orders"), token)).andReturn()).get(0).get("id").asLong();
        mvc.perform(bearer(post("/api/orders/" + orderId + "/cancel"), token)).andExpect(status().isOk());
        review(token, p, 5, "Cancelled it").andExpect(status().isForbidden());
    }

    @Test
    void productRatingAggregatesFollowReviewChanges() throws Exception {
        String a = registerCustomer();
        String b = registerCustomer();
        Product p = product("Keyboard", "50.00", 9);
        long first = reviewId(a, p, 5, "Love it");
        reviewId(b, p, 2, "Meh");

        mvc.perform(get("/api/products/" + p.getId()))
                .andExpect(jsonPath("$.ratingAvg").value(3.5)).andExpect(jsonPath("$.ratingCount").value(2));
        mvc.perform(get("/api/products/" + p.getId() + "/reviews"))
                .andExpect(jsonPath("$.summary.count").value(2))
                .andExpect(jsonPath("$.summary.distribution[4]").value(1))
                .andExpect(jsonPath("$.summary.distribution[1]").value(1));

        mvc.perform(bearer(delete("/api/reviews/" + first), a)).andExpect(status().isNoContent());
        mvc.perform(get("/api/products/" + p.getId()))
                .andExpect(jsonPath("$.ratingAvg").value(2.0)).andExpect(jsonPath("$.ratingCount").value(1));
    }

    @Test
    void authorCanEditAndDeleteOnlyInsideTheEditWindow() throws Exception {
        String token = registerCustomer();
        String other = registerCustomer();
        Product p = product("Mouse", "20.00", 5);
        long id = reviewId(token, p, 3, "Okay");

        mvc.perform(bearer(put("/api/reviews/" + id), other).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("rating", 1, "comment", "Hijack")))).andExpect(status().isForbidden());
        mvc.perform(bearer(put("/api/reviews/" + id), token).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("rating", 4, "title", "Better", "comment", "Grew on me"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(4)).andExpect(jsonPath("$.canEdit").value(true));

        Review r = reviewRepo.findById(id).orElseThrow();
        r.setCreatedAt(Instant.now().minusSeconds(60 * 10)); // 10 minutes ago, window is 5
        reviewRepo.saveAndFlush(r);

        mvc.perform(bearer(put("/api/reviews/" + id), token).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("rating", 5, "comment", "Too late")))).andExpect(status().isForbidden());
        mvc.perform(bearer(delete("/api/reviews/" + id), token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/products/" + p.getId() + "/reviews")).andExpect(jsonPath("$.reviews[0].canEdit").value(false));
    }

    @Test
    void votesAreOnePerUserAndToggle() throws Exception {
        String author = registerCustomer();
        String voter = registerCustomer();
        Product p = product("Speaker", "35.00", 5);
        long id = reviewId(author, p, 5, "Loud");
        String vote = "/api/reviews/" + id + "/vote";

        mvc.perform(bearer(post(vote), author).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("type", "HELPFUL")))).andExpect(status().isForbidden());
        mvc.perform(bearer(post(vote), voter).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("type", "HELPFUL"))))
                .andExpect(jsonPath("$.helpfulCount").value(1)).andExpect(jsonPath("$.myVote").value("HELPFUL"));
        // voting the same way again withdraws the vote instead of counting twice
        mvc.perform(bearer(post(vote), voter).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("type", "HELPFUL"))))
                .andExpect(jsonPath("$.helpfulCount").value(0)).andExpect(jsonPath("$.myVote").doesNotExist());
        mvc.perform(bearer(post(vote), voter).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("type", "HELPFUL")))).andExpect(status().isOk());
        // switching sides moves the vote
        mvc.perform(bearer(post(vote), voter).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("type", "UNHELPFUL"))))
                .andExpect(jsonPath("$.helpfulCount").value(0)).andExpect(jsonPath("$.unhelpfulCount").value(1));

        mvc.perform(bearer(get("/api/products/" + p.getId() + "/reviews"), voter))
                .andExpect(jsonPath("$.reviews[0].myVote").value("UNHELPFUL"));
        mvc.perform(post(vote).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("type", "HELPFUL"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reportedReviewsAreHiddenUntilAdminModeratesThem() throws Exception {
        String author = registerCustomer();
        String reporter = registerCustomer();
        String admin = adminToken();
        Product p = product("Webcam", "45.00", 5);
        long id = reviewId(author, p, 1, "Rude text here");

        mvc.perform(bearer(post("/api/reviews/" + id + "/report"), author)).andExpect(status().isForbidden());
        mvc.perform(bearer(post("/api/reviews/" + id + "/report"), reporter).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("reason", "Abusive")))).andExpect(status().isNoContent());

        // hidden from everyone else and excluded from the average, still visible to its author
        mvc.perform(get("/api/products/" + p.getId() + "/reviews")).andExpect(jsonPath("$.reviews.length()").value(0))
                .andExpect(jsonPath("$.summary.count").value(0));
        mvc.perform(bearer(get("/api/products/" + p.getId() + "/reviews"), author))
                .andExpect(jsonPath("$.reviews.length()").value(1)).andExpect(jsonPath("$.reviews[0].status").value("FLAGGED"));
        mvc.perform(bearer(get("/api/admin/reviews").param("status", "FLAGGED"), admin))
                .andExpect(jsonPath("$.totalItems").value(1)).andExpect(jsonPath("$.items[0].flagReason").value("Abusive"));

        // the moderator's edit fixes the wording, clears the flag and brings the review back
        mvc.perform(bearer(put("/api/admin/reviews/" + id), admin).contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("title", "Cleaned", "comment", "Not great"))))
                .andExpect(jsonPath("$.review.status").value("VISIBLE")).andExpect(jsonPath("$.review.editedByAdmin").value(true));
        mvc.perform(get("/api/products/" + p.getId() + "/reviews")).andExpect(jsonPath("$.reviews.length()").value(1))
                .andExpect(jsonPath("$.summary.average").value(1.0));

        // dismiss and delete
        mvc.perform(bearer(post("/api/admin/reviews/" + id + "/flag"), admin)).andExpect(jsonPath("$.review.status").value("FLAGGED"));
        mvc.perform(bearer(post("/api/admin/reviews/" + id + "/dismiss"), admin)).andExpect(jsonPath("$.review.status").value("VISIBLE"));
        mvc.perform(bearer(delete("/api/admin/reviews/" + id), admin)).andExpect(status().isNoContent());
        assertThat(reviewRepo.findById(id)).isEmpty();
    }

    @Test
    void dashboardListsUnratedPurchasesAndMyReviewsWithSearchAndSort() throws Exception {
        String token = registerCustomer();
        Product bought = product("Desk Lamp", "32.00", 5);
        Product reviewed = product("Power Bank", "28.00", 5);
        Product notBought = product("PC Case", "75.00", 5);
        buy(token, bought);
        reviewId(token, reviewed, 5, "Charges fast");

        mvc.perform(bearer(get("/api/me/unrated-products"), token))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].name").value("Desk Lamp"));
        mvc.perform(bearer(get("/api/me/reviews"), token)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(bearer(get("/api/me/reviews").param("q", "charges"), token)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(bearer(get("/api/me/reviews").param("q", "nothing like this"), token)).andExpect(jsonPath("$.length()").value(0));
        assertThat(notBought.getId()).isNotNull();
    }

    @Test
    void adminStatsSummariseReviewsAndOrders() throws Exception {
        String token = registerCustomer();
        String admin = adminToken();
        Product p = product("Stand", "15.00", 5);
        reviewId(token, p, 5, "Great stand");

        mvc.perform(bearer(get("/api/admin/stats"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.total").value(1))
                .andExpect(jsonPath("$.reviews.fiveStar").value(1))
                .andExpect(jsonPath("$.orderCount").value(1))
                .andExpect(jsonPath("$.revenue").value(18.99)); // £15 + £3.99 shipping
    }
}
