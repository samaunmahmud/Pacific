package com.pacific.marketplace.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    public record CartItemDto(Long productId, String name, String imageUrl, String categoryName, BigDecimal unitPrice,
                              int quantity, BigDecimal lineTotal, int stock, String sellerName, String sellerSlug) {
    }

    /** Each seller ships (and charges shipping for) their own part of the cart. */
    public record ShipmentDto(String sellerName, String sellerSlug, BigDecimal subtotal, BigDecimal shipping) {
    }

    public record CartDto(List<CartItemDto> items, List<ShipmentDto> shipments, int itemCount, BigDecimal subtotal,
                          BigDecimal shipping, BigDecimal total, BigDecimal freeShippingThreshold) {
    }

    public record AddToCartRequest(@NotNull Long productId, @NotNull @Min(1) @Max(100) Integer quantity) {
    }

    public record UpdateCartRequest(@NotNull @Min(0) @Max(100) Integer quantity) {
    }
}
