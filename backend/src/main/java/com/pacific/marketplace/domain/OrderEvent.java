package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "order_events")
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private OrderEventType type;

    /** A short human-readable detail, for example who cancelled the order or the tracking number. */
    @Column(length = 300)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderEvent() {
    }

    OrderEvent(Order order, OrderEventType type, String note) {
        this.order = order;
        this.type = type;
        this.note = note == null || note.isBlank() ? null : note.strip().substring(0, Math.min(note.strip().length(), 300));
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public OrderEventType getType() { return type; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
