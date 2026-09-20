package com.pacific.marketplace.domain;

/** Things that happen to an order, shown to the customer (and seller) as its timeline. */
public enum OrderEventType {
    /** Card checkout started; the customer still has to pay. */
    AWAITING_PAYMENT,
    PLACED,
    PAYMENT_RECEIVED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    RETURN_REQUESTED,
    RETURN_APPROVED,
    RETURN_REJECTED,
    RETURN_CANCELLED,
    REFUNDED
}
