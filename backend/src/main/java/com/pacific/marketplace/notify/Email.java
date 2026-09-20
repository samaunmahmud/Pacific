package com.pacific.marketplace.notify;

/**
 * A finished email. kind says what it is for (ORDER_CONFIRMATION, ...). recordedText is what goes in the admin email
 * log: the same as text, except for emails that carry a secret (a password reset link), where the secret is hidden so
 * nobody who can read the log can use it.
 */
public record Email(String to, String subject, String text, String html, String kind, String recordedText) {

    public Email(String to, String subject, String text, String html, String kind) {
        this(to, subject, text, html, kind, text);
    }
}
