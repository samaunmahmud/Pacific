package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** One message in a conversation, optionally about one of the store's products or one of the buyer's orders. */
@Entity
@Table(name = "messages")
public class Message {

    public static final int MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Column(nullable = false, length = MAX_LENGTH)
    private String body;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Message() {
    }

    public Message(Conversation conversation, User sender, String body, Product product, Order order) {
        this.conversation = conversation;
        this.sender = sender;
        this.body = body;
        this.product = product;
        this.order = order;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public User getSender() { return sender; }
    public String getBody() { return body; }
    public Product getProduct() { return product; }
    public Order getOrder() { return order; }
    public Instant getCreatedAt() { return createdAt; }
}
