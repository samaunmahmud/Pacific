package com.pacific.marketplace.service;

import java.text.Normalizer;
import java.util.Locale;

final class Text {

    private Text() {
    }

    /** Trims; returns null for null/blank. */
    static String clean(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    /** A LIKE pattern matching {@code s} anywhere, with wildcards in the user's input escaped (MySQL/H2 use '\'). */
    static String contains(String s) {
        String t = clean(s);
        if (t == null) return "%";
        String escaped = t.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    static String slugify(String name) {
        String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.toLowerCase(Locale.ROOT).replaceAll("&", " and ").replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "category" : s;
    }
}
