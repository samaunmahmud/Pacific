package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** "Save 10% with coupon" on one listing: shoppers clip it, and each customer can use it once, up to the budget. */
@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "percent_off", nullable = false)
    private int percentOff;

    /** How many customers can use it. */
    @Column(nullable = false)
    private int budget;

    /** Changed only by atomic updates (see CouponRepository). */
    @Column(nullable = false, updatable = false)
    private int used;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Coupon() {
    }

    public Coupon(Product product, int percentOff, int budget, Instant endsAt) {
        this.product = product;
        this.percentOff = percentOff;
        this.budget = budget;
        this.endsAt = endsAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isLive(Instant now) {
        return active && now.isBefore(endsAt) && used < budget;
    }

    public void deactivate() { this.active = false; }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public int getPercentOff() { return percentOff; }
    public int getBudget() { return budget; }
    public int getUsed() { return used; }
    public Instant getEndsAt() { return endsAt; }
    public boolean isActive() { return active; }
}
