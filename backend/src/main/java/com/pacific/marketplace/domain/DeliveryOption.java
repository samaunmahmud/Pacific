package com.pacific.marketplace.domain;

/** How a seller's part of an order is delivered. */
public enum DeliveryOption {
    /** Free over the seller's threshold, otherwise the flat rate. */
    STANDARD("Standard delivery"),
    /** A fixed charge for faster delivery. */
    EXPRESS("Express delivery");

    private final String label;

    DeliveryOption(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
