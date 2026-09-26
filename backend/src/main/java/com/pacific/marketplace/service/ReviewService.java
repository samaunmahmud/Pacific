package com.pacific.marketplace.service;

import com.pacific.marketplace.config.AppProperties;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.Review;
import com.pacific.marketplace.domain.ReviewStatus;
import com.pacific.marketplace.domain.ReviewVote;
import com.pacific.marketplace.domain.VoteType;
import com.pacific.marketplace.repo.OrderItemRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.RatingStats;
import com.pacific.marketplace.repo.ReviewRepository;
import com.pacific.marketplace.repo.ReviewVoteRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.PageResponse;
import com.pacific.marketplace.web.dto.ProductDtos.ProductDto;
import com.pacific.marketplace.web.dto.ReviewDtos.AdminReviewDto;
import com.pacific.marketplace.web.dto.ReviewDtos.ModerationEditRequest;
import com.pacific.marketplace.web.dto.ReviewDtos.ProductReviewsResponse;
import com.pacific.marketplace.web.dto.ReviewDtos.RatingSummary;
import com.pacific.marketplace.web.dto.ReviewDtos.ReviewDto;
import com.pacific.marketplace.web.dto.ReviewDtos.ReviewEligibility;
import com.pacific.marketplace.web.dto.ReviewDtos.ReviewRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private static final long ANONYMOUS = -1L;

    private final ReviewRepository reviews;
    private final ReviewVoteRepository votes;
    private final ProductRepository products;
    private final BuyBox buyBox;
    private final OrderItemRepository orderItems;
    private final UserRepository users;
    private final Duration editWindow;

    public ReviewService(ReviewRepository reviews, ReviewVoteRepository votes, ProductRepository products,
                         OrderItemRepository orderItems, UserRepository users, AppProperties props, BuyBox buyBox) {
        this.buyBox = buyBox;
        this.reviews = reviews;
        this.votes = votes;
        this.products = products;
        this.orderItems = orderItems;
        this.users = users;
        this.editWindow = Duration.ofMinutes(props.reviews().editWindowMinutes());
    }

    // ---------- storefront ----------

    /** {@code viewerId} is the signed-in customer, or null for anonymous visitors/admins. */
    @Transactional(readOnly = true)
    public ProductReviewsResponse forProduct(Long listingId, String sort, Long viewerId) {
        // Reviews live on the catalog page, shared by every seller's offer for the product.
        Long productId = catalogPage(listingId).getId();

        List<Review> list = reviews.findForProductPage(productId, viewerId == null ? ANONYMOUS : viewerId,
                sortFor(sort));
        List<ReviewDto> dtos = toDtos(list, viewerId);

        RatingStats stats = reviews.visibleStats(productId);
        int[] distribution = new int[5];
        for (Object[] row : reviews.ratingDistribution(productId)) {
            distribution[((Number) row[0]).intValue() - 1] = ((Number) row[1]).intValue();
        }
        RatingSummary summary = new RatingSummary(round(stats.avg()).doubleValue(), stats.count().intValue(),
                distribution);

        ReviewEligibility eligibility = new ReviewEligibility(false, false, false, null);
        if (viewerId != null) {
            Long own = list.stream().filter(r -> r.getUser().getId().equals(viewerId)).map(Review::getId)
                    .findFirst().orElse(null);
            boolean hasReviewed = own != null || reviews.existsByUserIdAndProductId(viewerId, productId);
            eligibility = new ReviewEligibility(true, orderItems.countPurchases(viewerId, productId) > 0,
                    hasReviewed, own);
        }
        return new ProductReviewsResponse(summary, dtos, eligibility);
    }

    @Transactional
    public ReviewDto create(Long userId, Long listingId, ReviewRequest req) {
        Product product = catalogPage(listingId);
        Long productId = product.getId();
        if (orderItems.countPurchases(userId, productId) == 0) {
            throw ApiException.forbidden("You can review a product once you've bought it.");
        }
        if (reviews.existsByUserIdAndProductId(userId, productId)) {
            throw ApiException.conflict("You have already reviewed this product.");
        }
        Review review = reviews.saveAndFlush(new Review(product, users.getReferenceById(userId), req.rating(),
                Text.clean(req.title()), req.comment().strip(), Text.clean(req.imageUrl())));
        recalculateRating(product);
        return ReviewDto.from(review, userId, null, editWindow, Instant.now());
    }

    @Transactional
    public ReviewDto update(Long userId, Long reviewId, ReviewRequest req) {
        Review review = ownEditableReview(userId, reviewId);
        review.edit(req.rating(), Text.clean(req.title()), req.comment().strip(), Text.clean(req.imageUrl()));
        reviews.flush();
        recalculateRating(review.getProduct());
        return ReviewDto.from(review, userId, null, editWindow, Instant.now()); // authors can't vote on their own review
    }

    @Transactional
    public void delete(Long userId, Long reviewId) {
        Review review = ownEditableReview(userId, reviewId);
        Product product = review.getProduct();
        reviews.delete(review);
        reviews.flush();
        recalculateRating(product);
    }

    /** Casting the same vote again withdraws it; casting the other one switches. */
    @Transactional
    public ReviewDto vote(Long userId, Long reviewId, VoteType type) {
        Review review = reviews.findWithUserAndProductById(reviewId)
                .orElseThrow(() -> ApiException.notFound("Review not found."));
        if (review.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("You can't vote on your own review.");
        }
        if (review.getStatus() != ReviewStatus.VISIBLE) {
            throw ApiException.notFound("Review not found.");
        }
        ReviewVote existing = votes.findByReviewIdAndUserId(reviewId, userId).orElse(null);
        VoteType mine = type;
        if (existing == null) {
            votes.save(new ReviewVote(review, users.getReferenceById(userId), type));
        } else if (existing.getVoteType() == type) {
            votes.delete(existing);
            mine = null;
        } else {
            existing.setVoteType(type);
        }
        votes.flush();
        // Recount instead of +/-1 so concurrent votes can't drift the totals.
        review.setHelpfulCount((int) votes.countByReviewIdAndVoteType(reviewId, VoteType.HELPFUL));
        review.setUnhelpfulCount((int) votes.countByReviewIdAndVoteType(reviewId, VoteType.UNHELPFUL));
        return ReviewDto.from(review, userId, mine, editWindow, Instant.now());
    }

    /** Any customer can report someone else's review; it's hidden from the product page until an admin decides. */
    @Transactional
    public void report(Long userId, Long reviewId, String reason) {
        Review review = reviews.findWithUserAndProductById(reviewId)
                .orElseThrow(() -> ApiException.notFound("Review not found."));
        if (review.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("You can't report your own review.");
        }
        if (review.getStatus() == ReviewStatus.FLAGGED) return; // already awaiting moderation
        review.flag(Text.clean(reason));
        reviews.flush();
        recalculateRating(review.getProduct());
    }

    // ---------- customer dashboard ----------

    @Transactional(readOnly = true)
    public List<ReviewDto> mine(Long userId, String q, String sort) {
        List<Review> list = reviews.findMine(userId, Text.contains(q), sortFor(sort));
        return toDtos(list, userId);
    }

    /** Products the customer bought but hasn't reviewed yet ("Items you recently purchased"). */
    @Transactional(readOnly = true)
    public List<ProductDto> unratedPurchases(Long userId) {
        return orderItems.findUnratedPurchasedProducts(userId).stream().map(ProductDto::from).toList();
    }

    // ---------- admin moderation ----------

    @Transactional(readOnly = true)
    public PageResponse<AdminReviewDto> adminList(ReviewStatus status, String q, int page, int size) {
        Specification<Review> spec = (root, cq, cb) -> cb.conjunction();
        if (status != null) spec = spec.and((root, cq, cb) -> cb.equal(root.get("status"), status));
        String query = Text.clean(q);
        if (query != null) {
            String pattern = Text.contains(query);
            spec = spec.and((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("comment")), pattern),
                    cb.like(cb.lower(cb.coalesce(root.<String>get("title"), "")), pattern)));
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.of(reviews.findAll(spec, pageable), this::adminDto);
    }

    @Transactional(readOnly = true)
    public AdminReviewDto adminGet(Long reviewId) {
        return adminDto(adminReview(reviewId));
    }

    /** Moderator edit: fixes the wording and clears the flag, like the old "Edit Flagged Review" screen. */
    @Transactional
    public AdminReviewDto adminEdit(Long reviewId, ModerationEditRequest req) {
        Review review = adminReview(reviewId);
        review.edit(review.getRating(), Text.clean(req.title()), req.comment().strip(), review.getImageUrl());
        review.clearFlag();
        review.setEditedByAdmin(true);
        reviews.flush();
        recalculateRating(review.getProduct());
        return adminDto(review);
    }

    /** "Reject flag": the review is fine as written. */
    @Transactional
    public AdminReviewDto adminDismissFlag(Long reviewId) {
        Review review = adminReview(reviewId);
        review.clearFlag();
        reviews.flush();
        recalculateRating(review.getProduct());
        return adminDto(review);
    }

    @Transactional
    public AdminReviewDto adminFlag(Long reviewId, String reason) {
        Review review = adminReview(reviewId);
        review.flag(Text.clean(reason));
        reviews.flush();
        recalculateRating(review.getProduct());
        return adminDto(review);
    }

    @Transactional
    public void adminDelete(Long reviewId) {
        Review review = adminReview(reviewId);
        Product product = review.getProduct();
        reviews.delete(review);
        reviews.flush();
        recalculateRating(product);
    }

    // ---------- helpers ----------

    private Review adminReview(Long id) {
        return reviews.findWithUserAndProductById(id).orElseThrow(() -> ApiException.notFound("Review not found."));
    }

    private AdminReviewDto adminDto(Review r) {
        return new AdminReviewDto(ReviewDto.from(r, null, null, editWindow, Instant.now()), r.getFlagReason());
    }

    private Review ownEditableReview(Long userId, Long reviewId) {
        Review review = reviews.findWithUserAndProductById(reviewId)
                .orElseThrow(() -> ApiException.notFound("Review not found."));
        if (!review.getUser().getId().equals(userId)) {
            throw ApiException.forbidden("You can only change your own reviews.");
        }
        // The window runs from posting time; editing does not extend it.
        if (!Instant.now().isBefore(review.getCreatedAt().plus(editWindow))) {
            throw ApiException.forbidden("Reviews can only be changed within " + editWindow.toMinutes()
                    + " minutes of posting.");
        }
        return review;
    }

    private List<ReviewDto> toDtos(List<Review> list, Long viewerId) {
        Map<Long, VoteType> myVotes = new HashMap<>();
        if (viewerId != null && !list.isEmpty()) {
            Collection<Long> ids = list.stream().map(Review::getId).toList();
            votes.findByUserIdAndReviewIdIn(viewerId, ids)
                    .forEach(v -> myVotes.put(v.getReview().getId(), v.getVoteType()));
        }
        Instant now = Instant.now();
        return list.stream()
                .map(r -> ReviewDto.from(r, viewerId, myVotes.get(r.getId()), editWindow, now))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** The catalog page for a listing, if any of its listings is on sale. */
    private Product catalogPage(Long listingId) {
        Long catalogId = products.findById(listingId).map(Product::catalogId).orElse(listingId);
        return products.findById(catalogId).filter(p -> buyBox.catalogVisible(p.getId()))
                .orElseThrow(() -> ApiException.notFound("Product not found."));
    }

    /** Keeps the product's cached average/count in sync (only visible reviews count). */
    public void recalculateRating(Product product) {
        RatingStats stats = reviews.visibleStats(product.getId());
        product.setRating(round(stats.avg()), stats.count().intValue());
    }

    private static BigDecimal round(Double avg) {
        return avg == null ? BigDecimal.ZERO.setScale(2) : BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
    }

    private static Sort sortFor(String sort) {
        String s = sort == null ? "newest" : sort.toLowerCase(Locale.ROOT);
        Sort.Order newest = Sort.Order.desc("createdAt");
        return switch (s) {
            case "highest" -> Sort.by(Sort.Order.desc("rating"), newest);
            case "lowest" -> Sort.by(Sort.Order.asc("rating"), newest);
            case "helpful" -> Sort.by(Sort.Order.desc("helpfulCount"), newest);
            default -> Sort.by(newest);
        };
    }
}
