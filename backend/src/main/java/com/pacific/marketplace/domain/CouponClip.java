package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** A shopper has clipped a coupon; orderId is set when it's used (and cleared if that order is cancelled). */
@Entity
@Table(name = "coupon_clips")
public class CouponClip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id")
    private Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "clipped_at", nullable = false, updatable = false)
    private Instant clippedAt;

    protected CouponClip() {
    }

    public CouponClip(Coupon coupon, User user) {
        this.coupon = coupon;
        this.user = user;
    }

    @PrePersist
    void onCreate() {
        clippedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Coupon getCoupon() { return coupon; }
    public Long getOrderId() { return orderId; }
}
