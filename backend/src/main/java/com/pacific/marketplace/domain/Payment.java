package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "checkout_ref", nullable = false, length = 36)
    private String checkoutRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private PaymentProviderType provider;

    @Column(name = "provider_session_id", length = 200)
    private String providerSessionId;

    /** Stripe PaymentIntent id once paid (used for refunds). */
    @Column(name = "provider_payment_ref", length = 200)
    private String providerPaymentRef;

    @Column(name = "checkout_url", length = 1000)
    private String checkoutUrl;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "refunded_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Payment() {
    }

    public Payment(String checkoutRef, User user, PaymentProviderType provider, String providerSessionId,
                   String checkoutUrl, BigDecimal amount, String currency, Instant expiresAt) {
        this.checkoutRef = checkoutRef;
        this.user = user;
        this.provider = provider;
        this.providerSessionId = providerSessionId;
        this.checkoutUrl = checkoutUrl;
        this.amount = amount;
        this.currency = currency;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markPaid(String providerPaymentRef) {
        this.status = PaymentStatus.PAID;
        this.providerPaymentRef = providerPaymentRef;
        this.paidAt = Instant.now();
    }

    public void close(PaymentStatus status) {
        this.status = status;
    }

    public void addRefund(BigDecimal amount) {
        this.refundedAmount = refundedAmount.add(amount);
    }

    public BigDecimal refundable() {
        return amount.subtract(refundedAmount);
    }

    public Long getId() { return id; }
    public String getCheckoutRef() { return checkoutRef; }
    public User getUser() { return user; }
    public PaymentProviderType getProvider() { return provider; }
    public String getProviderSessionId() { return providerSessionId; }
    public String getProviderPaymentRef() { return providerPaymentRef; }
    public void setProviderPaymentRef(String ref) { this.providerPaymentRef = ref; }
    public String getCheckoutUrl() { return checkoutUrl; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
}
