package com.pacific.marketplace.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import com.pacific.marketplace.domain.DeliveryOption;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    /** unitPrice is after promotions; listUnitPrice (the regular price) and promotion are set when one applies. */
    public record CartItemDto(Long productId, String name, String imageUrl, String categoryName, BigDecimal unitPrice,
                              int quantity, BigDecimal lineTotal, int stock, String sellerName, String sellerSlug,
                              BigDecimal listUnitPrice, String promotion) {
    }

    /** A promo code applied to the cart: how much it takes off which store's items. */
    public record PromoDto(String code, int percentOff, String storeName, BigDecimal discount) {
    }

    /** One way to deliver a shipment: its price and the days it would arrive if ordered now. */
    public record DeliveryChoiceDto(DeliveryOption option, String label, BigDecimal fee, LocalDate from, LocalDate to) {
    }

    /**
     * Each seller ships (and charges delivery for) their own part of the cart. {@code key} identifies it at checkout
     * (the seller's slug, or "pacific"); shipping is the standard price, and toFreeDelivery how much more would make
     * standard delivery free (zero once it is).
     */
    public record ShipmentDto(String key, String sellerName, String sellerSlug, BigDecimal subtotal, BigDecimal shipping,
                              BigDecimal freeThreshold, BigDecimal toFreeDelivery, List<DeliveryChoiceDto> choices) {
    }

    /** {@code saved} are "saved for later" lines, not counted in the totals. orderWithin: today's order cut-off. */
    public record CartDto(List<CartItemDto> items, List<ShipmentDto> shipments, int itemCount, BigDecimal subtotal,
                          BigDecimal shipping, BigDecimal total, BigDecimal freeShippingThreshold,
                          List<CartItemDto> saved, Instant orderWithin, PromoDto promo, String promoError) {
    }

    public record AddToCartRequest(@NotNull Long productId, @NotNull @Min(1) @Max(100) Integer quantity) {
    }

    public record UpdateCartRequest(@NotNull @Min(0) @Max(100) Integer quantity) {
    }
}
