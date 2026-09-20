package com.pacific.marketplace.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "orders")
public class Order {

    public static final String PAY_ON_DELIVERY = "PAY_ON_DELIVERY";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** Null = sold by Pacific. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private SellerProfile seller;

    /** Orders created by the same checkout share this reference. */
    @Column(name = "checkout_ref", length = 36)
    private String checkoutRef;

    /** Commission percent in force when the order was placed (later rate changes don't touch it). */
    @Column(name = "commission_rate", precision = 5, scale = 2)
    private BigDecimal commissionRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PLACED;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal shipping;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod = PAY_ON_DELIVERY;

    @Embedded
    private ShippingAddress address;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "tracking_carrier", length = 60)
    private String trackingCarrier;

    @Column(name = "tracking_number", length = 80)
    private String trackingNumber;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /** What has happened to the order, oldest first. */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("createdAt ASC, id ASC")
    @BatchSize(size = 50)
    private List<OrderEvent> events = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected Order() {
    }

    public Order(User user, ShippingAddress address, SellerProfile seller, String checkoutRef,
                 BigDecimal commissionRate) {
        this.user = user;
        this.address = address;
        this.seller = seller;
        this.checkoutRef = checkoutRef;
        this.commissionRate = commissionRate;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addItem(Product product, int quantity) {
        items.add(new OrderItem(this, product, quantity));
    }

    public void addEvent(OrderEventType type, String note) {
        events.add(new OrderEvent(this, type, note));
    }

    /** Called when the order is handed to the carrier. Tracking details are optional (a seller may deliver by hand). */
    public void markShipped(String carrier, String number) {
        this.trackingCarrier = carrier;
        this.trackingNumber = number;
        this.shippedAt = Instant.now();
    }

    public void markDelivered() {
        this.deliveredAt = Instant.now();
    }

    public void setTotals(BigDecimal subtotal, BigDecimal shipping) {
        this.subtotal = subtotal;
        this.shipping = shipping;
        this.total = subtotal.add(shipping);
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public SellerProfile getSeller() { return seller; }
    public String getCheckoutRef() { return checkoutRef; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getShipping() { return shipping; }
    public BigDecimal getTotal() { return total; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public ShippingAddress getAddress() { return address; }
    public List<OrderItem> getItems() { return items; }
    public String getTrackingCarrier() { return trackingCarrier; }
    public String getTrackingNumber() { return trackingNumber; }
    public Instant getShippedAt() { return shippedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public List<OrderEvent> getEvents() { return events; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
