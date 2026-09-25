package com.pacific.marketplace.web.dto;

import com.pacific.marketplace.domain.Coupon;
import com.pacific.marketplace.domain.LightningDeal;
import com.pacific.marketplace.domain.PromoCode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PromotionDtos {

    private PromotionDtos() {
    }

    /** startsAt null = now. */
    public record DealRequest(
            @NotNull(message = "Choose a listing.") Long productId,
            @NotNull(message = "Enter the deal price.") @DecimalMin(value = "0.01", message = "The deal price must be above zero.")
            @Digits(integer = 8, fraction = 2, message = "Use at most 2 decimals.") BigDecimal price,
            @NotNull(message = "How many units at the deal price?") @Min(value = 1, message = "At least 1 unit.")
            @Max(value = 10_000) Integer quantity,
            Instant startsAt,
            @NotNull(message = "How long should it run?") @Min(value = 1, message = "At least 1 hour.")
            @Max(value = 12, message = "At most 12 hours.") Integer hours) {
    }

    public record CouponRequest(
            @NotNull(message = "Choose a listing.") Long productId,
            @NotNull(message = "How much off?") @Min(value = 5, message = "At least 5% off.") @Max(value = 50, message = "At most 50% off.")
            Integer percentOff,
            @NotNull(message = "How many customers can use it?") @Min(value = 1, message = "At least 1 use.") @Max(value = 100_000)
            Integer budget,
            @NotNull(message = "How many days should it run?") @Min(value = 1, message = "At least 1 day.") @Max(value = 90, message = "At most 90 days.")
            Integer days) {
    }

    public record CodeRequest(
            @NotBlank(message = "Choose a code.")
            @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "Codes are 4 to 20 letters and numbers.") String code,
            @NotNull(message = "How much off?") @Min(value = 5, message = "At least 5% off.") @Max(value = 50, message = "At most 50% off.")
            Integer percentOff,
            @DecimalMin(value = "0.00", message = "The minimum spend can't be negative.") @DecimalMax("100000.00")
            @Digits(integer = 6, fraction = 2, message = "Use at most 2 decimals.") BigDecimal minSpend,
            @Min(value = 1, message = "Allow at least 1 use.") @Max(value = 1_000_000) Integer maxUses,
            @NotNull(message = "How many days should it run?") @Min(value = 1, message = "At least 1 day.") @Max(value = 90, message = "At most 90 days.")
            Integer days) {
    }

    /** status: SCHEDULED, LIVE, SOLD_OUT or ENDED. */
    public record StoreDealDto(Long id, Long productId, String productName, BigDecimal price, BigDecimal regularPrice,
                               int quantity, int claimed, Instant startsAt, Instant endsAt, String status) {
        public static StoreDealDto from(LightningDeal d, Instant now) {
            String status = now.isBefore(d.getStartsAt()) ? "SCHEDULED" : !now.isBefore(d.getEndsAt()) ? "ENDED"
                    : d.getClaimed() >= d.getQuantity() ? "SOLD_OUT" : "LIVE";
            return new StoreDealDto(d.getId(), d.getProduct().getId(), d.getProduct().getName(), d.getDealPrice(),
                    d.getProduct().getPrice(), d.getQuantity(), d.getClaimed(), d.getStartsAt(), d.getEndsAt(), status);
        }
    }

    public record StoreCouponDto(Long id, Long productId, String productName, int percentOff, int budget, int used,
                                 Instant endsAt, boolean live) {
        public static StoreCouponDto from(Coupon c, Instant now) {
            return new StoreCouponDto(c.getId(), c.getProduct().getId(), c.getProduct().getName(), c.getPercentOff(),
                    c.getBudget(), c.getUsed(), c.getEndsAt(), c.isLive(now));
        }
    }

    public record StoreCodeDto(Long id, String code, int percentOff, BigDecimal minSpend, Integer maxUses, int used,
                               Instant endsAt, boolean live) {
        public static StoreCodeDto from(PromoCode c, Instant now) {
            return new StoreCodeDto(c.getId(), c.getCode(), c.getPercentOff(), c.getMinSpend(), c.getMaxUses(),
                    c.getUsed(), c.getEndsAt(), c.isLive(now));
        }
    }

    /** Everything a store is running, scheduled, or ran in the last 30 days. */
    public record StorePromotionsDto(List<StoreDealDto> deals, List<StoreCouponDto> coupons, List<StoreCodeDto> codes) {
    }
}
