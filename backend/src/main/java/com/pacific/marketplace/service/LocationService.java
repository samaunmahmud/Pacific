package com.pacific.marketplace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacific.marketplace.security.AttemptLimiter;
import com.pacific.marketplace.web.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Where a shopper wants things delivered, for the "Deliver to" in the top bar: a UK postcode, or the postcode nearest
 * their device's location, looked up with postcodes.io (free, no key; UK government open data). Lookups go through
 * the shop, not the shopper's browser, and are cached. If postcodes.io can't be reached, a well-formed postcode is
 * still accepted, just without the place name.
 */
@Service
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    /** The shape of a UK postcode (loosely: postcodes.io decides whether it really exists). */
    private static final Pattern POSTCODE = Pattern.compile("^[A-Z]{1,2}[0-9][A-Z0-9]?[0-9][A-Z]{2}$");
    private static final int CACHE_SIZE = 5_000;

    /** A place to deliver to: "Hillingdon" and "UB8 1AA"; place is null when it couldn't be looked up. */
    public record Location(String postcode, String outcode, String place, String country) {
    }

    private final String apiBase;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final AttemptLimiter limiter;
    private final Map<String, Location> cache = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Location> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    public LocationService(@Value("${app.location.api-base:https://api.postcodes.io}") String apiBase,
                           @Value("${app.location.hourly-limit:120}") int hourlyLimit) {
        this.apiBase = apiBase.replaceAll("/+$", "");
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.limiter = new AttemptLimiter(Clock.systemUTC(), hourlyLimit, Duration.ofHours(1),
                "Too many location lookups.");
    }

    /** "ub81aa" → "UB8 1AA", or null if it isn't shaped like a UK postcode. */
    public static String normalise(String raw) {
        if (raw == null) return null;
        String compact = raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (!POSTCODE.matcher(compact).matches()) return null;
        return compact.substring(0, compact.length() - 3) + " " + compact.substring(compact.length() - 3);
    }

    public Location postcode(String raw, String caller) {
        String postcode = normalise(raw);
        if (postcode == null) throw ApiException.badRequest("Enter a UK postcode, like UB8 1AA.");
        Location cached = cache.get(postcode);
        if (cached != null) return cached;
        spend(caller);
        JsonNode body;
        try {
            body = get("/postcodes/" + URLEncoder.encode(postcode, StandardCharsets.UTF_8).replace("+", "%20"));
        } catch (Unavailable e) {
            return new Location(postcode, postcode.substring(0, postcode.indexOf(' ')), null, null);
        }
        if (body == null || !body.path("result").isObject()) {
            throw ApiException.notFound("We couldn't find " + postcode + ". Check it and try again.");
        }
        return remember(from(body.get("result")));
    }

    public Location near(double latitude, double longitude, String caller) {
        if (latitude < 49 || latitude > 61 || longitude < -9 || longitude > 2.5) {
            throw ApiException.badRequest("We only deliver in the UK, and you seem to be somewhere else.");
        }
        spend(caller);
        JsonNode body;
        try {
            body = get(String.format(Locale.ROOT, "/postcodes?lat=%.6f&lon=%.6f&limit=1&radius=2000", latitude, longitude));
        } catch (Unavailable e) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "We can't look up your location right now. Please enter your postcode instead.");
        }
        JsonNode results = body == null ? null : body.path("result");
        if (results == null || !results.isArray() || results.isEmpty()) {
            throw ApiException.notFound("We couldn't find a UK postcode near you. Please enter it instead.");
        }
        return remember(from(results.get(0)));
    }

    private void spend(String caller) {
        String key = caller == null ? "?" : caller;
        limiter.check(key);
        limiter.hit(key);
    }

    private Location remember(Location l) {
        cache.put(l.postcode(), l);
        return l;
    }

    private static Location from(JsonNode r) {
        String place = text(r, "admin_district");
        if (place == null) place = text(r, "parish");
        return new Location(text(r, "postcode"), text(r, "outcode"), place, text(r, "country"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private static final class Unavailable extends Exception {
        Unavailable(String message) {
            super(message);
        }
    }

    /** The response body, or null for a 404. */
    private JsonNode get(String path) throws Unavailable {
        HttpRequest req = HttpRequest.newBuilder(URI.create(apiBase + path)).timeout(Duration.ofSeconds(4))
                .header("Accept", "application/json").GET().build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404) return null;
            if (res.statusCode() != 200) throw new Unavailable("HTTP " + res.statusCode());
            return json.readTree(res.body());
        } catch (IOException e) {
            log.warn("Postcode lookup failed: {}", e.toString());
            throw new Unavailable(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Unavailable("interrupted");
        }
    }
}
