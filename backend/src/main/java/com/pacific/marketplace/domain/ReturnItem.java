package com.pacific.marketplace.domain;

import jakarta.persistence.*;

/** How many units of one order line are being returned. */
@Entity
@Table(name = "return_items")
public class ReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_id")
    private ReturnRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @Column(nullable = false)
    private int quantity;

    protected ReturnItem() {
    }

    ReturnItem(ReturnRequest request, OrderItem orderItem, int quantity) {
        this.request = request;
        this.orderItem = orderItem;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public OrderItem getOrderItem() { return orderItem; }
    public int getQuantity() { return quantity; }
}
