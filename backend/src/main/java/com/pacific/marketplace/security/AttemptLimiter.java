package com.pacific.marketplace.security;

import com.pacific.marketplace.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;

/**
 * Allows a limited number of failed attempts per key inside a time window, then answers 429 until the window ends.
 * Kept in memory, so it is per server instance (fine for one server; a shop with several would need a shared store).
 * The window starts at the first failure and is not extended by later ones, so a blocked key always recovers.
 */
public final class AttemptLimiter {

    private record Entry(int hits, Instant windowEnds) {
    }

    private final Clock clock;
    private final int max;
    private final Duration window;
    private final String message;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public AttemptLimiter(Clock clock, int max, Duration window, String message) {
        this.clock = clock;
        this.max = Math.max(1, max);
        this.window = window;
        this.message = message;
    }

    /** Throws 429 if the key has used up its attempts. */
    public void check(String key) {
        Entry e = entries.get(key);
        if (e == null) return;
        Instant now = clock.instant();
        if (!now.isBefore(e.windowEnds())) {
            entries.remove(key, e);
            return;
        }
        if (e.hits() >= max) {
            long minutes = Math.max(1, (long) Math.ceil(Duration.between(now, e.windowEnds()).toSeconds() / 60.0));
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    message + " Please try again in " + minutes + " minute" + (minutes == 1 ? "" : "s") + ".");
        }
    }

    /** Records one failed attempt. */
    public void hit(String key) {
        Instant now = clock.instant();
        entries.merge(key, new Entry(1, now.plus(window)), (old, fresh) ->
                now.isBefore(old.windowEnds()) ? new Entry(old.hits() + 1, old.windowEnds()) : fresh);
        if (entries.size() > 50_000) purge();
    }

    /** Forgets the key (after a successful sign-in). */
    public void clear(String key) {
        entries.remove(key);
    }

    public void purge() {
        Instant now = clock.instant();
        entries.entrySet().removeIf(en -> !now.isBefore(en.getValue().windowEnds()));
    }
}
