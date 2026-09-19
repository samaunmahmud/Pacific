package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id")
    private SellerProfile seller;

    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 12)
    private LedgerType type;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 200)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(SellerProfile seller, Long orderId, LedgerType type, BigDecimal amount, String note) {
        this.seller = seller;
        this.orderId = orderId;
        this.type = type;
        this.amount = amount;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public SellerProfile getSeller() { return seller; }
    public Long getOrderId() { return orderId; }
    public LedgerType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
