package com.pacific.marketplace.web.dto;

import com.pacific.marketplace.domain.Review;
import com.pacific.marketplace.domain.ReviewStatus;
import com.pacific.marketplace.domain.VoteType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    public record ReviewRequest(
            @NotNull(message = "Please choose a star rating.") @Min(value = 1, message = "Please choose a star rating.")
            @Max(5) Integer rating,
            @Size(max = 120, message = "Title can be at most 120 characters.") String title,
            @NotBlank(message = "Please write a comment.") @Size(max = 500, message = "Comment can be at most 500 characters.")
            String comment,
            @Size(max = 500) @Pattern(regexp = ProductDtos.HTTP_URL, message = "Image URL must start with http:// or https://")
            String imageUrl) {
    }

    public record VoteRequest(@NotNull(message = "Vote type is required.") VoteType type) {
    }

    public record ReportRequest(@Size(max = 200) String reason) {
    }

    public record ModerationEditRequest(
            @Size(max = 120) String title,
            @NotBlank(message = "Comment can't be empty.") @Size(max = 500) String comment) {
    }

    public record ReviewDto(Long id, Long productId, String productName, int rating, String title, String comment,
                            String imageUrl, ReviewStatus status, String flagReason, String authorName,
                            Instant createdAt, Instant updatedAt, int helpfulCount, int unhelpfulCount,
                            VoteType myVote, boolean mine, boolean canEdit, Instant editableUntil,
                            boolean editedByAdmin) {

        /** Builds the DTO as seen by {@code viewerId} (null = anonymous). Must run inside a transaction. */
        public static ReviewDto from(Review r, Long viewerId, VoteType myVote, Duration editWindow, Instant now) {
            boolean mine = viewerId != null && viewerId.equals(r.getUser().getId());
            Instant until = r.getCreatedAt().plus(editWindow);
            boolean canEdit = mine && now.isBefore(until);
            return new ReviewDto(r.getId(), r.getProduct().getId(), r.getProduct().getName(), r.getRating(),
                    r.getTitle(), r.getComment(), r.getImageUrl(), r.getStatus(),
                    // only the author sees why their review was flagged; admins get the admin DTO path
                    mine ? r.getFlagReason() : null, r.getUser().getName(), r.getCreatedAt(), r.getUpdatedAt(),
                    r.getHelpfulCount(), r.getUnhelpfulCount(), myVote, mine, canEdit, mine ? until : null,
                    r.isEditedByAdmin());
        }
    }

    public record RatingSummary(double average, int count, int[] distribution) {
    }

    /** What the signed-in customer can do on this product page. */
    public record ReviewEligibility(boolean signedIn, boolean purchased, boolean hasReviewed, Long ownReviewId) {
    }

    public record ProductReviewsResponse(RatingSummary summary, List<ReviewDto> reviews, ReviewEligibility eligibility) {
    }

    /** Admin moderation view: includes the report reason for any review. */
    public record AdminReviewDto(ReviewDto review, String flagReason) {
    }
}
