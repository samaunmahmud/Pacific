package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** A buyer's private thread with one seller's store. Unread counts and the latest message are kept here for inboxes. */
@Entity
@Table(name = "conversations")
public class Conversation {

    public static final int PREVIEW_LENGTH = 140;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id")
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id")
    private SellerProfile seller;

    /** Changed only by atomic updates in ConversationRepository, so two messages at once can't lose a count. */
    @Column(name = "buyer_unread", nullable = false)
    private int buyerUnread;

    @Column(name = "seller_unread", nullable = false)
    private int sellerUnread;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    @Column(name = "last_preview", nullable = false, length = PREVIEW_LENGTH)
    private String lastPreview;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Conversation() {
    }

    public Conversation(User buyer, SellerProfile seller) {
        this.buyer = buyer;
        this.seller = seller;
        this.lastMessageAt = Instant.now();
        this.lastPreview = "";
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /** A one-line preview of a message for inbox lists. */
    public static String preview(String body) {
        String line = body.strip().replaceAll("\\s+", " ");
        return line.length() <= PREVIEW_LENGTH ? line : line.substring(0, PREVIEW_LENGTH - 1) + "…";
    }

    public boolean isBuyer(Long userId) { return buyer.getId().equals(userId); }
    public boolean isSellerUser(Long userId) { return seller.getUser().getId().equals(userId); }

    public Long getId() { return id; }
    public User getBuyer() { return buyer; }
    public SellerProfile getSeller() { return seller; }
    public int getBuyerUnread() { return buyerUnread; }
    public int getSellerUnread() { return sellerUnread; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public String getLastPreview() { return lastPreview; }
    public Instant getCreatedAt() { return createdAt; }
}
