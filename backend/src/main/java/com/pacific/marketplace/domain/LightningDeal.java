package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** A deal price on one listing for a few hours, on a limited number of units ("45% claimed"). */
@Entity
@Table(name = "lightning_deals")
public class LightningDeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "deal_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal dealPrice;

    @Column(nullable = false)
    private int quantity;

    /** Changed only by atomic updates (see LightningDealRepository), so the deal can't be oversold. */
    @Column(nullable = false, updatable = false)
    private int claimed;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LightningDeal() {
    }

    public LightningDeal(Product product, BigDecimal dealPrice, int quantity, Instant startsAt, Instant endsAt) {
        this.product = product;
        this.dealPrice = dealPrice;
        this.quantity = quantity;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /** Ends the deal now (it stays in the history). */
    public void endNow(Instant now) {
        if (endsAt.isAfter(now)) endsAt = now.isBefore(startsAt) ? startsAt : now;
    }

    public boolean isLive(Instant now) {
        return !now.isBefore(startsAt) && now.isBefore(endsAt);
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public BigDecimal getDealPrice() { return dealPrice; }
    public int getQuantity() { return quantity; }
    public int getClaimed() { return claimed; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
}
