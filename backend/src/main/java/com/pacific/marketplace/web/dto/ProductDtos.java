package com.pacific.marketplace.web.dto;

import com.pacific.marketplace.domain.Category;
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

    /** sellerName is "Pacific" for house products (sellerSlug is then null). moreImages follow imageUrl in a gallery. */
    public record ProductDto(Long id, String name, String description, BigDecimal price, BigDecimal listPrice,
                             int discountPercent, int stock, String imageUrl, List<String> moreImages,
                             CategoryDto category, boolean active,
                             double ratingAvg, int ratingCount, String sellerName, String sellerSlug,
                             Instant createdAt) {
        public static final String HOUSE_STORE = "Pacific";

        public static ProductDto from(Product p) {
            var seller = p.getSeller();
            return new ProductDto(p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getListPrice(),
                    p.getDiscountPercent(), p.getStock(), p.getImageUrl(), p.getMoreImages(), CategoryDto.from(p.getCategory()),
                    p.isActive(), p.getRatingAvg().doubleValue(), p.getRatingCount(),
                    seller == null ? HOUSE_STORE : seller.getStoreName(), seller == null ? null : seller.getSlug(),
                    p.getCreatedAt());
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
            Boolean active) {
    }

    public record StockRequest(@NotNull @Min(0) @Max(1_000_000) Integer stock) {
    }

    public record CategoryRequest(@NotBlank(message = "Category name is required.") @Size(max = 80) String name) {
    }
}
