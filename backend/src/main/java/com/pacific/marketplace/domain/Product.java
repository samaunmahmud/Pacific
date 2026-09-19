package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /** Null = sold by Pacific itself (the house store run by admins). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private SellerProfile seller;

    /** Optional "was" price shown struck through; when above the price the product counts as a deal. */
    @Column(name = "list_price", precision = 10, scale = 2)
    private BigDecimal listPrice;

    @Column(name = "discount_percent", nullable = false)
    private int discountPercent;

    @Column(nullable = false)
    private boolean active = true;

    /** Average of visible reviews. Recomputed by ReviewService whenever a review changes. */
    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Product() {
    }

    public Product(String name, String description, BigDecimal price, int stock, String imageUrl, Category category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.imageUrl = imageUrl;
        this.category = category;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        recomputeDiscount();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        recomputeDiscount();
    }

    public void update(String name, String description, BigDecimal price, BigDecimal listPrice, int stock,
                       String imageUrl, Category category, boolean active) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.listPrice = listPrice;
        this.stock = stock;
        this.imageUrl = imageUrl;
        this.category = category;
        this.active = active;
        recomputeDiscount();
    }

    /** Keeps the stored discount in step with price/listPrice so deals can be filtered and sorted in SQL. */
    public void recomputeDiscount() {
        if (listPrice != null && listPrice.compareTo(price) > 0) {
            discountPercent = listPrice.subtract(price).multiply(BigDecimal.valueOf(100))
                    .divide(listPrice, 0, java.math.RoundingMode.DOWN).intValue();
        } else {
            discountPercent = 0;
        }
    }

    /** Can shoppers see and buy this? Hidden products and products of unapproved sellers can't be. */
    public boolean isVisibleInStore() {
        return active && (seller == null || seller.isApproved());
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    public String getImageUrl() { return imageUrl; }
    public Category getCategory() { return category; }
    public SellerProfile getSeller() { return seller; }
    public void setSeller(SellerProfile seller) { this.seller = seller; }
    public BigDecimal getListPrice() { return listPrice; }
    public int getDiscountPercent() { return discountPercent; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public BigDecimal getRatingAvg() { return ratingAvg; }
    public int getRatingCount() { return ratingCount; }
    public void setRating(BigDecimal avg, int count) { this.ratingAvg = avg; this.ratingCount = count; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
