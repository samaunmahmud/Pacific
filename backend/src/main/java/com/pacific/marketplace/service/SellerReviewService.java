package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.OrderStatus;
import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.domain.SellerReview;
import com.pacific.marketplace.repo.OrderRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.RatingStats;
import com.pacific.marketplace.repo.SellerProfileRepository;
import com.pacific.marketplace.repo.SellerReviewRepository;
import com.pacific.marketplace.repo.UserRepository;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.ReviewDtos.RatingSummary;
import com.pacific.marketplace.web.dto.SellerDtos.PublicSellerDto;
import com.pacific.marketplace.web.dto.SellerDtos.SellerRatingEligibility;
import com.pacific.marketplace.web.dto.SellerDtos.SellerRatingRequest;
import com.pacific.marketplace.web.dto.SellerDtos.SellerReviewDto;
import com.pacific.marketplace.web.dto.SellerDtos.SellerStorePage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public seller storefronts and the buyer ratings shown on them (separate from product reviews). */
@Service
public class SellerReviewService {

    private final SellerProfileRepository sellers;
    private final SellerReviewRepository ratings;
    private final OrderRepository orders;
    private final ProductRepository products;
    private final UserRepository users;

    public SellerReviewService(SellerProfileRepository sellers, SellerReviewRepository ratings, OrderRepository orders,
                               ProductRepository products, UserRepository users) {
        this.sellers = sellers;
        this.ratings = ratings;
        this.orders = orders;
        this.products = products;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public SellerStorePage page(String slug, Long viewerId) {
        SellerProfile seller = approved(slug);
        List<SellerReviewDto> reviews = ratings.findTop50BySellerIdOrderByCreatedAtDescIdDesc(seller.getId()).stream()
                .map(r -> SellerReviewDto.from(r, viewerId)).toList();
        int[] distribution = new int[5];
        for (Object[] row : ratings.distribution(seller.getId())) {
            distribution[((Number) row[0]).intValue() - 1] = ((Number) row[1]).intValue();
        }
        RatingSummary summary = new RatingSummary(seller.getRatingAvg().doubleValue(), seller.getRatingCount(),
                distribution);
        boolean own = viewerId != null && seller.getUser().getId().equals(viewerId);
        boolean canRate = viewerId != null && !own && hasDeliveredOrder(viewerId, seller);
        return new SellerStorePage(PublicSellerDto.from(seller, products.countBySellerId(seller.getId())), summary,
                reviews, new SellerRatingEligibility(viewerId != null, canRate, own));
    }

    /** Create or update the caller's rating. Only buyers with a delivered order from this seller can rate. */
    @Transactional
    public SellerReviewDto rate(Long userId, String slug, SellerRatingRequest req) {
        SellerProfile seller = approved(slug);
        if (seller.getUser().getId().equals(userId) || !hasDeliveredOrder(userId, seller)) {
            throw ApiException.forbidden("You can rate a seller once an order from them has been delivered.");
        }
        String comment = Text.clean(req.comment());
        SellerReview review = ratings.findBySellerIdAndUserId(seller.getId(), userId).orElse(null);
        if (review == null) {
            review = ratings.save(new SellerReview(seller, users.getReferenceById(userId), req.rating(), comment));
        } else {
            review.edit(req.rating(), comment);
        }
        ratings.flush();
        recalculate(seller);
        return SellerReviewDto.from(review, userId);
    }

    @Transactional
    public void deleteMine(Long userId, String slug) {
        SellerProfile seller = approved(slug);
        ratings.findBySellerIdAndUserId(seller.getId(), userId).ifPresent(r -> {
            ratings.delete(r);
            ratings.flush();
            recalculate(seller);
        });
    }

    @Transactional
    public void adminDelete(Long reviewId) {
        SellerReview review = ratings.findById(reviewId).orElseThrow(() -> ApiException.notFound("Rating not found."));
        SellerProfile seller = review.getSeller();
        ratings.delete(review);
        ratings.flush();
        recalculate(seller);
    }

    private boolean hasDeliveredOrder(Long userId, SellerProfile seller) {
        return orders.existsByUserIdAndSellerIdAndStatus(userId, seller.getId(), OrderStatus.DELIVERED);
    }

    private SellerProfile approved(String slug) {
        return sellers.findBySlug(slug).filter(SellerProfile::isApproved)
                .orElseThrow(() -> ApiException.notFound("Store not found."));
    }

    private void recalculate(SellerProfile seller) {
        RatingStats stats = ratings.stats(seller.getId());
        BigDecimal avg = stats.avg() == null ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(stats.avg()).setScale(2, RoundingMode.HALF_UP);
        seller.setRating(avg, stats.count().intValue());
    }
}
