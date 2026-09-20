package com.pacific.marketplace.domain;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The couriers sellers commonly use, and where a customer can follow a parcel. Anything else is just shown as text. */
public final class Carriers {

    private static final Map<String, String> TRACKING = Map.of(
            "royal mail", "https://www.royalmail.com/track-your-item#/tracking-results/%s",
            "dpd", "https://track.dpd.co.uk/search?reference=%s",
            "evri", "https://www.evri.com/track-a-parcel/%s",
            "dhl", "https://www.dhl.com/gb-en/home/tracking/tracking-express.html?submit=1&tracking-id=%s",
            "ups", "https://www.ups.com/track?tracknum=%s",
            "fedex", "https://www.fedex.com/fedextrack/?trknbr=%s",
            "parcelforce", "https://www.parcelforce.com/track-trace?trackNumber=%s");

    public static final List<String> NAMES = List.of("Royal Mail", "DPD", "Evri", "DHL", "UPS", "FedEx", "Parcelforce");

    private Carriers() {
    }

    /** A link to follow the parcel, or null when the courier isn't one we know or there is no tracking number. */
    public static String trackingUrl(String carrier, String number) {
        if (carrier == null || number == null || number.isBlank()) return null;
        String pattern = TRACKING.get(carrier.strip().toLowerCase(Locale.ROOT));
        return pattern == null ? null : pattern.formatted(URLEncoder.encode(number.strip(), StandardCharsets.UTF_8));
    }
}
