package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A code typed at checkout for a percentage off one store's items (seller null = Pacific's own store). Each customer
 * can use it once; it can need a minimum spend at that store and have a limit on uses.
 */
@Entity
@Table(name = "promo_codes")
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored in capitals; matched ignoring case. */
    @Column(nullable = false, length = 30, updatable = false)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private SellerProfile seller;

    @Column(name = "percent_off", nullable = false)
    private int percentOff;

    @Column(name = "min_spend", nullable = false, precision = 10, scale = 2)
    private BigDecimal minSpend = BigDecimal.ZERO;

    @Column(name = "max_uses")
    private Integer maxUses;

    /** Changed only by atomic updates (see PromoCodeRepository). */
    @Column(nullable = false, updatable = false)
    private int used;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PromoCode() {
    }

    public PromoCode(String code, SellerProfile seller, int percentOff, BigDecimal minSpend, Integer maxUses, Instant endsAt) {
        this.code = code;
        this.seller = seller;
        this.percentOff = percentOff;
        this.minSpend = minSpend == null ? BigDecimal.ZERO : minSpend;
        this.maxUses = maxUses;
        this.endsAt = endsAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isLive(Instant now) {
        return active && now.isBefore(endsAt) && (maxUses == null || used < maxUses);
    }

    public void deactivate() { this.active = false; }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public SellerProfile getSeller() { return seller; }
    public int getPercentOff() { return percentOff; }
    public BigDecimal getMinSpend() { return minSpend; }
    public Integer getMaxUses() { return maxUses; }
    public int getUsed() { return used; }
    public Instant getEndsAt() { return endsAt; }
    public boolean isActive() { return active; }
}
