package com.pacific.marketplace.service;

import com.pacific.marketplace.web.ApiException;
import java.util.Locale;
import java.util.Set;

/**
 * Turns down passwords that are easy to guess, on top of the 8-character minimum: the most common ones (whatever the
 * capitals, and with digits or symbols tacked on the end) and ones built from the account's own email or name.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    /** Lower-case, with trailing digits and symbols removed ("Password123!" is "password"). */
    private static final Set<String> COMMON = Set.copyOf(java.util.List.of(
            "password", "passw0rd", "p@ssword", "p@ssw0rd", "passwort", "motdepasse", "qwerty", "qwertyuiop", "qwertyui",
            "asdfghjk", "asdfghjkl", "zxcvbnm", "azerty", "iloveyou", "letmein", "welcome", "welcomeback", "admin",
            "administrator", "abc", "abcdef", "abcdefg", "abcdefgh", "monkey", "dragon", "football", "baseball",
            "sunshine", "princess", "starwars", "whatever", "trustno", "superman", "batman", "shadow", "master",
            "michael", "jennifer", "charlie", "freedom", "computer", "secret", "changeme", "default", "login",
            "test", "testing", "guest", "user", "pacific", "shopping", "marketplace", "", "a", "aa", "aaa", "aaaa",
            "aaaaaaaa", "qwe", "qwer", "qwerty", "q1w2e3r4", "1q2w3e4r", "zaq1zaq1", "1qaz2wsx", "loveme",
            "lovely", "hello", "helloworld", "cheese", "summer", "winter", "spring", "autumn", "london", "england",
            "manchester", "liverpool", "arsenal", "chelsea", "google", "facebook", "amazon", "samsung", "iphone"));

    public static void check(String password, String email, String name) {
        String lower = password.toLowerCase(Locale.ROOT);
        String core = lower.replaceAll("[^a-z]+$", "");
        if (COMMON.contains(core) || lower.chars().distinct().count() <= 2 || isSequence(lower)) {
            throw ApiException.badRequest("That password is too easy to guess. Try a few unrelated words together, "
                    + "like \"river-lamp-orbit\".");
        }
        String local = email == null ? "" : email.toLowerCase(Locale.ROOT).split("@")[0].replaceAll("[^a-z0-9]", "");
        String compact = lower.replaceAll("[^a-z0-9]", "");
        if (local.length() >= 4 && (compact.contains(local) || local.contains(compact))) {
            throw ApiException.badRequest("Your password can't be based on your email address.");
        }
        if (name != null) {
            for (String part : name.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (part.length() >= 4 && compact.replaceAll("[0-9]+$", "").equals(part)) {
                    throw ApiException.badRequest("Your password can't be just your name.");
                }
            }
        }
    }

    /** "12345678", "87654321", "abcdefgh": each character one up or one down from the last. */
    private static boolean isSequence(String s) {
        if (s.length() < 4) return false;
        int step = s.charAt(1) - s.charAt(0);
        if (Math.abs(step) != 1) return false;
        for (int i = 2; i < s.length(); i++) if (s.charAt(i) - s.charAt(i - 1) != step) return false;
        return true;
    }
}
