package com.pacific.marketplace.domain;

/** The condition an offer is sold in. */
public enum ItemCondition {
    NEW("New"),
    USED_LIKE_NEW("Used – like new"),
    USED_GOOD("Used – good"),
    USED_ACCEPTABLE("Used – acceptable");

    private final String label;

    ItemCondition(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
