package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

/** A customer's request to send back part (or all) of a delivered order, and what became of it. */
@Entity
@Table(name = "return_requests")
public class ReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ReturnStatus status = ReturnStatus.REQUESTED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnReason reason;

    /** What the customer wrote. */
    @Column(length = 500)
    private String comment;

    /** What the seller wrote: instructions when approving, or why it was refused. */
    @Column(name = "seller_note", length = 300)
    private String sellerNote;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    /** Whether the returned units went back on sale. */
    @Column(nullable = false)
    private boolean restocked;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<ReturnItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected ReturnRequest() {
    }

    public ReturnRequest(Order order, ReturnReason reason, String comment) {
        this.order = order;
        this.reason = reason;
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

    public void addItem(OrderItem orderItem, int quantity) {
        items.add(new ReturnItem(this, orderItem, quantity));
    }

    public int totalUnits() {
        return items.stream().mapToInt(ReturnItem::getQuantity).sum();
    }

    /** What the returned goods cost (delivery not included). */
    public BigDecimal itemsValue() {
        return items.stream().map(i -> i.getOrderItem().getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }

    public void approve(String note) {
        this.status = ReturnStatus.APPROVED;
        this.sellerNote = note;
    }

    public void reject(String note) {
        this.status = ReturnStatus.REJECTED;
        this.sellerNote = note;
        this.resolvedAt = Instant.now();
    }

    public void cancel() {
        this.status = ReturnStatus.CANCELLED;
        this.resolvedAt = Instant.now();
    }

    public void refunded(BigDecimal amount, boolean restocked, String note) {
        this.status = ReturnStatus.REFUNDED;
        this.refundAmount = amount;
        this.restocked = restocked;
        if (note != null) this.sellerNote = note;
        this.resolvedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public ReturnStatus getStatus() { return status; }
    public ReturnReason getReason() { return reason; }
    public String getComment() { return comment; }
    public String getSellerNote() { return sellerNote; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public boolean isRestocked() { return restocked; }
    public List<ReturnItem> getItems() { return items; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
