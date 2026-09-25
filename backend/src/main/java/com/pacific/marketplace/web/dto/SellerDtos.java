package com.pacific.marketplace.web.dto;

import com.pacific.marketplace.domain.LedgerEntry;
import com.pacific.marketplace.domain.LedgerType;
import com.pacific.marketplace.domain.OrderStatus;
import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.domain.SellerReview;
import com.pacific.marketplace.domain.SellerStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SellerDtos {

    private SellerDtos() {
    }

    /** freeDeliveryThreshold null: use the shop's default. */
    public record DeliverySettingsRequest(
            @DecimalMin(value = "0.00", message = "The free delivery amount can't be negative.")
            @DecimalMax(value = "100000.00") @Digits(integer = 6, fraction = 2, message = "Use at most 2 decimals.")
            BigDecimal freeDeliveryThreshold,
            @NotNull(message = "Choose how long you take to dispatch.")
            @Min(value = 0, message = "Dispatch time can't be negative.") @Max(value = 5, message = "Dispatch within 5 business days.")
            Integer dispatchDays) {
    }

    public record ApplyRequest(
            @NotBlank(message = "Please choose a store name.")
            @Size(min = 3, max = 80, message = "Store name must be 3 to 80 characters.") String storeName,
            @Size(max = 1000) String description) {
    }

    /**
     * The seller's own view of their profile. freeDeliveryThreshold is null while the shop's default applies
     * (defaultFreeDelivery).
     */
    public record SellerDto(Long id, String storeName, String slug, String description, SellerStatus status,
                            String statusNote, BigDecimal commissionPercent, boolean commissionOverridden,
                            double ratingAvg, int ratingCount, Instant createdAt, Instant approvedAt,
                            BigDecimal freeDeliveryThreshold, BigDecimal defaultFreeDelivery, int dispatchDays) {
        public static SellerDto from(SellerProfile s, BigDecimal effectiveCommission, BigDecimal defaultFreeDelivery) {
            return new SellerDto(s.getId(), s.getStoreName(), s.getSlug(), s.getDescription(), s.getStatus(),
                    s.getStatusNote(), effectiveCommission, s.getCommissionOverride() != null,
                    s.getRatingAvg().doubleValue(), s.getRatingCount(), s.getCreatedAt(), s.getApprovedAt(),
                    s.getFreeDeliveryThreshold(), defaultFreeDelivery, s.getDispatchDays());
        }
    }

    public record PublicSellerDto(Long id, String storeName, String slug, String description, double ratingAvg,
                                  int ratingCount, Instant since, long productCount) {
        public static PublicSellerDto from(SellerProfile s, long productCount) {
            return new PublicSellerDto(s.getId(), s.getStoreName(), s.getSlug(), s.getDescription(),
                    s.getRatingAvg().doubleValue(), s.getRatingCount(), s.getApprovedAt() != null ? s.getApprovedAt()
                    : s.getCreatedAt(), productCount);
        }
    }

    public record LowStock(Long productId, String name, int stock) {
    }

    public record SellerStats(BigDecimal grossSales, long orderCount, Map<OrderStatus, Long> ordersByStatus,
                              long unitsSold, BigDecimal balance, long unansweredQuestions, long openReturns, long productCount,
                              List<LowStock> lowStock, int lowStockThreshold) {
    }

    public record LedgerEntryDto(Long id, Long orderId, LedgerType type, BigDecimal amount, String note,
                                 Instant createdAt) {
        public static LedgerEntryDto from(LedgerEntry e) {
            return new LedgerEntryDto(e.getId(), e.getOrderId(), e.getType(), e.getAmount(), e.getNote(),
                    e.getCreatedAt());
        }
    }

    /** commission is net of what was given back on refunds; refunds is what the seller repaid customers. */
    public record EarningsDto(BigDecimal balance, BigDecimal sales, BigDecimal commission, BigDecimal payouts,
                              BigDecimal refunds, PageResponse<LedgerEntryDto> entries) {
    }

    // ----- admin -----

    public record AdminSellerDto(SellerDto seller, String ownerName, String ownerEmail, BigDecimal balance,
                                 long productCount) {
    }

    public record SellerStatusRequest(@NotNull(message = "Status is required.") SellerStatus status,
                                      @Size(max = 300) String note) {
    }

    /** {@code percent} null removes the per-seller override (the marketplace default applies again). */
    public record CommissionRequest(
            @DecimalMin(value = "0.00", message = "Commission can't be negative.")
            @DecimalMax(value = "100.00", message = "Commission can't exceed 100%.")
            @Digits(integer = 3, fraction = 2, message = "Commission can have at most 2 decimals.") BigDecimal percent) {
    }

    public record DefaultCommissionRequest(
            @NotNull(message = "Commission is required.")
            @DecimalMin(value = "0.00", message = "Commission can't be negative.")
            @DecimalMax(value = "100.00", message = "Commission can't exceed 100%.")
            @Digits(integer = 3, fraction = 2, message = "Commission can have at most 2 decimals.") BigDecimal percent) {
    }

    public record PayoutRequest(
            @NotNull(message = "Amount is required.") @DecimalMin(value = "0.01", message = "Amount must be positive.")
            @Digits(integer = 8, fraction = 2, message = "Amount can have at most 2 decimals.") BigDecimal amount,
            @Size(max = 200) String note) {
    }

    // ----- seller ratings -----

    public record SellerRatingRequest(
            @NotNull(message = "Please choose a star rating.") @Min(value = 1, message = "Please choose a star rating.")
            @Max(5) Integer rating,
            @Size(max = 500, message = "Comment can be at most 500 characters.") String comment) {
    }

    public record SellerReviewDto(Long id, int rating, String comment, String authorName, Instant createdAt,
                                  boolean mine) {
        public static SellerReviewDto from(SellerReview r, Long viewerId) {
            return new SellerReviewDto(r.getId(), r.getRating(), r.getComment(), r.getUser().getName(),
                    r.getCreatedAt(), viewerId != null && viewerId.equals(r.getUser().getId()));
        }
    }

    public record SellerRatingEligibility(boolean signedIn, boolean canRate, boolean ownStore) {
    }

    public record SellerStorePage(PublicSellerDto seller, ReviewDtos.RatingSummary summary,
                                  List<SellerReviewDto> reviews, SellerRatingEligibility eligibility) {
    }
}
