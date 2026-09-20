package com.pacific.marketplace.notify;

/** A finished email. kind says what it is for (ORDER_CONFIRMATION, ...), for the sent-email record. */
public record Email(String to, String subject, String text, String html, String kind) {
}
