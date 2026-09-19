package com.pacific.marketplace.domain;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    PLACED, PROCESSING, SHIPPED, DELIVERED, CANCELLED;

    /** Statuses an order in this status may be moved to by an admin. */
    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case PLACED -> EnumSet.of(PROCESSING, CANCELLED);
            case PROCESSING -> EnumSet.of(SHIPPED, CANCELLED);
            case SHIPPED -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }
}
