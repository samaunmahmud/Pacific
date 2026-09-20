package com.pacific.marketplace.domain;

public enum ReturnReason {
    DAMAGED("Arrived damaged or faulty"),
    NOT_AS_DESCRIBED("Not as described"),
    WRONG_ITEM("Wrong item received"),
    NO_LONGER_NEEDED("No longer needed"),
    OTHER("Other");

    private final String label;

    ReturnReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
