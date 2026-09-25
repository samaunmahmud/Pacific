package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** A customer used a promo code on an order (one per customer; removed if that order is cancelled). */
@Entity
@Table(name = "promo_redemptions")
public class PromoRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promo_id")
    private PromoCode promo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PromoRedemption() {
    }

    public PromoRedemption(PromoCode promo, User user, Long orderId) {
        this.promo = promo;
        this.user = user;
        this.orderId = orderId;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
}
