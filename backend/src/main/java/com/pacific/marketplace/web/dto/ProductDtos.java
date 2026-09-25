package com.pacific.marketplace.web.dto;

import com.pacific.marketplace.domain.Category;
import com.pacific.marketplace.domain.ItemCondition;
import com.pacific.marketplace.domain.Product;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ProductDtos {

    private ProductDtos() {
    }

    public static final String HTTP_URL = "^$|^https?://\\S+$";
    /**
     * A web address, a photo uploaded to the shop (see ImageService), or a demo-data drawing ("demo:kind:hue", sent
     * back unchanged when a demo product is edited).
     */
    public static final String PRODUCT_IMAGE =
            "^$|^https?://\\S+$|^/api/images/[0-9a-f]{32}\\.(jpg|png)$|^demo:[a-z-]{1,30}:\\d{1,3}$";

    public record CategoryDto(Long id, String name, String slug) {
        public static CategoryDto from(Category c) {
            return c == null ? null : new CategoryDto(c.getId(), c.getName(), c.getSlug());
        }
    }

    /**
     * sellerName is "Pacific" for house products (sellerSlug is then null). moreImages follow imageUrl in a gallery.
     * catalogId is the product page this listing belongs to (its own id unless it's another seller's offer); on a
     * catalog page, boxPrice is what the buy box charges and offerCount how many listings are on sale; in storefront
     * results, boxProductId/boxStock/boxSellerName describe the listing "Add to cart" buys (see ProductService).
     */
    public record ProductDto(Long id, Long catalogId, String name, String description, BigDecimal price,
                             BigDecimal listPrice, int discountPercent, int stock, String imageUrl,
                             List<String> moreImages, CategoryDto category, boolean active, ItemCondition condition,
                             BigDecimal boxPrice, int offerCount, Long boxProductId, int boxStock, String boxSellerName,
                             double ratingAvg, int ratingCount, String sellerName, String sellerSlug,
                             Instant createdAt) {
        public static final String HOUSE_STORE = "Pacific";

        public static ProductDto from(Product p) {
            var seller = p.getSeller();
            return new ProductDto(p.getId(), p.catalogId(), p.getName(), p.getDescription(), p.getPrice(), p.getListPrice(),
                    p.getDiscountPercent(), p.getStock(), p.getImageUrl(), p.getMoreImages(), CategoryDto.from(p.getCategory()),
                    p.isActive(), p.getCondition(), p.isOffer() ? null : p.getBoxPrice(), p.getOfferCount(),
                    p.getId(), p.getStock(), seller == null ? HOUSE_STORE : seller.getStoreName(),
                    p.getRatingAvg().doubleValue(), p.getRatingCount(),
                    seller == null ? HOUSE_STORE : seller.getStoreName(), seller == null ? null : seller.getSlug(),
                    p.getCreatedAt());
        }

        /** The same product page with its live buy box (another seller's listing may be the one on sale). */
        public ProductDto withBuyBox(Long productId, BigDecimal price, int stock, String sellerName, int offers) {
            return new ProductDto(id, catalogId, name, description, this.price, listPrice, discountPercent, this.stock,
                    imageUrl, moreImages, category, active, condition, price, offers, productId, stock, sellerName,
                    ratingAvg, ratingCount, this.sellerName, sellerSlug, createdAt);
        }
    }

    public record ProductRequest(
            @NotBlank(message = "Product name is required.") @Size(max = 160) String name,
            @Size(max = 2000) String description,
            @NotNull(message = "Price is required.") @DecimalMin(value = "0.00", message = "Price can't be negative.")
            @DecimalMax(value = "99999999.99") @Digits(integer = 8, fraction = 2, message = "Price can have at most 2 decimals.")
            BigDecimal price,
            @DecimalMin(value = "0.01", message = "List price must be above zero.")
            @Digits(integer = 8, fraction = 2, message = "List price can have at most 2 decimals.") BigDecimal listPrice,
            @NotNull(message = "Stock is required.") @Min(value = 0, message = "Stock can't be negative.")
            @Max(1_000_000) Integer stock,
            @Size(max = 500) @Pattern(regexp = PRODUCT_IMAGE, message = "Image URL must start with http:// or https://")
            String imageUrl,
            /** Photos after the main one, in order. Null leaves them as they are; an empty list removes them. */
            @Size(max = Product.MAX_MORE_IMAGES, message = "A product can have at most 8 photos.")
            List<@NotBlank @Size(max = 500)
                 @Pattern(regexp = PRODUCT_IMAGE, message = "Photo links must start with http:// or https://") String> moreImages,
            Long categoryId,
            Boolean active,
            /** Only for offers on another seller's catalog page (a catalog page's own listing is always new). */
            ItemCondition condition) {
    }

    /** Another seller joins a product's page with their own price, stock and condition. */
    public record OfferRequest(
            @NotNull(message = "Price is required.") @DecimalMin(value = "0.01", message = "Price must be above zero.")
            @DecimalMax(value = "99999999.99") @Digits(integer = 8, fraction = 2, message = "Price can have at most 2 decimals.")
            BigDecimal price,
            @DecimalMin(value = "0.01", message = "List price must be above zero.")
            @Digits(integer = 8, fraction = 2, message = "List price can have at most 2 decimals.") BigDecimal listPrice,
            @NotNull(message = "Stock is required.") @Min(value = 0, message = "Stock can't be negative.")
            @Max(1_000_000) Integer stock,
            @NotNull(message = "Choose the condition.") ItemCondition condition) {
    }

    /** One seller's listing on a product page, as the buy box and "Other sellers" show it. */
    public record OfferDto(Long productId, String sellerName, String sellerSlug, double sellerRating,
                           int sellerRatingCount, BigDecimal price, BigDecimal listPrice, int discountPercent, int stock,
                           ItemCondition condition, String conditionLabel, boolean buyBox) {
    }

    public record StockRequest(@NotNull @Min(0) @Max(1_000_000) Integer stock) {
    }

    public record CategoryRequest(@NotBlank(message = "Category name is required.") @Size(max = 80) String name) {
    }
}
