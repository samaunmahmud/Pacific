package com.pacific.marketplace.domain;

public enum ReturnStatus {
    /** The customer asked to return items; the seller has not answered yet. */
    REQUESTED,
    /** The seller agreed; the customer sends the items back and the seller refunds once they arrive. */
    APPROVED,
    REJECTED,
    /** The refund was issued. Final. */
    REFUNDED,
    /** The customer withdrew the request before it was answered. */
    CANCELLED;

    /** Counts against the quantity that can still be returned: requested, agreed, or already refunded. */
    public boolean holdsQuantity() {
        return this == REQUESTED || this == APPROVED || this == REFUNDED;
    }
}
