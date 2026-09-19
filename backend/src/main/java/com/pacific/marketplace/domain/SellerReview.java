package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** A buyer's rating of a seller (separate from product reviews). */
@Entity
@Table(name = "seller_reviews")
public class SellerReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id")
    private SellerProfile seller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private int rating;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected SellerReview() {
    }

    public SellerReview(SellerProfile seller, User user, int rating, String comment) {
        this.seller = seller;
        this.user = user;
        this.rating = rating;
        this.comment = comment;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void edit(int rating, String comment) {
        this.rating = rating;
        this.comment = comment;
    }

    public Long getId() { return id; }
    public SellerProfile getSeller() { return seller; }
    public User getUser() { return user; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
}
