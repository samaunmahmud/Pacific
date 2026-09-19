package com.pacific.marketplace.domain;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    /** Card checkout started but not yet paid. Stock is reserved; sellers can't see the order yet. */
    AWAITING_PAYMENT, PLACED, PROCESSING, SHIPPED, DELIVERED, CANCELLED;

    /** Statuses an order in this status may be moved to by an admin. */
    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PLACED -> EnumSet.of(PROCESSING, CANCELLED);
            case PROCESSING -> EnumSet.of(SHIPPED, CANCELLED);
            case SHIPPED -> EnumSet.of(DELIVERED);
            // AWAITING_PAYMENT is driven by the payment (paid -> PLACED, expired/cancelled -> CANCELLED), never by hand
            case AWAITING_PAYMENT, DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }
}
