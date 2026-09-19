package com.pacific.marketplace.domain;

public enum LedgerType {
    /** Money the seller earned from a delivered order (items + shipping). Positive. */
    SALE,
    /** The marketplace's cut of the item subtotal. Negative. */
    COMMISSION,
    /** A payout recorded by an admin. Negative. */
    PAYOUT
}
