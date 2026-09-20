package com.pacific.marketplace.domain;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/** Formats amounts for people to read, in the shop's currency. */
public final class Money {

    private Money() {
    }

    public static String format(BigDecimal amount, String currency) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.UK);
        f.setCurrency(Currency.getInstance(currency == null ? "GBP" : currency));
        return f.format(amount);
    }
}
