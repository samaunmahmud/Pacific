package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "seller_profiles")
@BatchSize(size = 50)
public class SellerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "store_name", nullable = false, length = 80)
    private String storeName;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SellerStatus status = SellerStatus.PENDING;

    @Column(name = "status_note", length = 300)
    private String statusNote;

    /** Null = use the marketplace default. */
    @Column(name = "commission_override", precision = 5, scale = 2)
    private BigDecimal commissionOverride;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected SellerProfile() {
    }

    public SellerProfile(User user, String storeName, String slug, String description) {
        this.user = user;
        this.storeName = storeName;
        this.slug = slug;
        this.description = description;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void setStatus(SellerStatus status, String note) {
        this.status = status;
        this.statusNote = note;
        if (status == SellerStatus.APPROVED && approvedAt == null) approvedAt = Instant.now();
    }

    public boolean isApproved() {
        return status == SellerStatus.APPROVED;
    }

    public void updateStore(String storeName, String description) {
        this.storeName = storeName;
        this.description = description;
    }

    public void setRating(BigDecimal avg, int count) {
        this.ratingAvg = avg;
        this.ratingCount = count;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getStoreName() { return storeName; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public SellerStatus getStatus() { return status; }
    public String getStatusNote() { return statusNote; }
    public BigDecimal getCommissionOverride() { return commissionOverride; }
    public void setCommissionOverride(BigDecimal commissionOverride) { this.commissionOverride = commissionOverride; }
    public BigDecimal getRatingAvg() { return ratingAvg; }
    public int getRatingCount() { return ratingCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getApprovedAt() { return approvedAt; }
}
