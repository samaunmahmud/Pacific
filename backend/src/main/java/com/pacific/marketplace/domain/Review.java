package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private int rating;

    @Column(length = 120)
    private String title;

    @Column(nullable = false, length = 1000)
    private String comment;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReviewStatus status = ReviewStatus.VISIBLE;

    @Column(name = "flag_reason", length = 200)
    private String flagReason;

    @Column(name = "helpful_count", nullable = false)
    private int helpfulCount;

    @Column(name = "unhelpful_count", nullable = false)
    private int unhelpfulCount;

    @Column(name = "edited_by_admin", nullable = false)
    private boolean editedByAdmin;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Review() {
    }

    public Review(Product product, User user, int rating, String title, String comment, String imageUrl) {
        this.product = product;
        this.user = user;
        this.rating = rating;
        this.title = title;
        this.comment = comment;
        this.imageUrl = imageUrl;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void edit(int rating, String title, String comment, String imageUrl) {
        this.rating = rating;
        this.title = title;
        this.comment = comment;
        this.imageUrl = imageUrl;
    }

    public void flag(String reason) {
        this.status = ReviewStatus.FLAGGED;
        this.flagReason = reason;
    }

    public void clearFlag() {
        this.status = ReviewStatus.VISIBLE;
        this.flagReason = null;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public User getUser() { return user; }
    public int getRating() { return rating; }
    public String getTitle() { return title; }
    public String getComment() { return comment; }
    public String getImageUrl() { return imageUrl; }
    public ReviewStatus getStatus() { return status; }
    public void setStatus(ReviewStatus status) { this.status = status; }
    public String getFlagReason() { return flagReason; }
    public int getHelpfulCount() { return helpfulCount; }
    public void setHelpfulCount(int helpfulCount) { this.helpfulCount = helpfulCount; }
    public int getUnhelpfulCount() { return unhelpfulCount; }
    public void setUnhelpfulCount(int unhelpfulCount) { this.unhelpfulCount = unhelpfulCount; }
    public boolean isEditedByAdmin() { return editedByAdmin; }
    public void setEditedByAdmin(boolean editedByAdmin) { this.editedByAdmin = editedByAdmin; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
