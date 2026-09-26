package com.pacific.marketplace;

import com.pacific.marketplace.config.AppProperties;
import com.pacific.marketplace.domain.DeliveryOption;
import com.pacific.marketplace.service.Delivery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Delivery prices and dates: business days, the 2pm cut-off, and each seller's dispatch time. */
class DeliveryTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    private static Delivery at(String localDateTime) {
        AppProperties props = new AppProperties(null, null, null,
                new AppProperties.Shop("GBP", new BigDecimal("3.99"), new BigDecimal("50"), 10, 30,
                        new BigDecimal("5.99"), 2, 3, 1, 14, "Europe/London"),
                null, null, null, null, null, null, null);
        ZonedDateTime now = java.time.LocalDateTime.parse(localDateTime).atZone(LONDON);
        return new Delivery(props, Clock.fixed(now.toInstant(), LONDON));
    }

    @Test
    void anOrderBeforeTheCutOffCountsFromToday() {
        Delivery d = at("2026-09-28T10:00"); // a Monday morning
        Delivery.Window standard = d.window(DeliveryOption.STANDARD, null); // dispatched Tuesday, 2-3 days
        assertThat(standard.from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(standard.to()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(d.window(DeliveryOption.EXPRESS, null).from()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(d.orderWithin()).isEqualTo(ZonedDateTime.of(2026, 9, 28, 14, 0, 0, 0, LONDON).toInstant());
    }

    @Test
    void lateOrdersAndWeekendsStartFromTheNextBusinessDay() {
        Delivery friday = at("2026-10-02T16:30"); // after the cut-off: counts from Monday, dispatched Tuesday
        assertThat(friday.window(DeliveryOption.STANDARD, null).from()).isEqualTo(LocalDate.of(2026, 10, 8));
        assertThat(friday.window(DeliveryOption.EXPRESS, null).from()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(friday.orderWithin()).isNull();

        Delivery saturday = at("2026-10-03T09:00");
        assertThat(saturday.window(DeliveryOption.EXPRESS, null).from()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(saturday.orderWithin()).isNull();
    }

    @Test
    void standardIsFreeOverTheThresholdAndExpressAlwaysCosts() {
        Delivery d = at("2026-09-28T10:00");
        assertThat(d.fee(DeliveryOption.STANDARD, new BigDecimal("49.99"), null)).isEqualByComparingTo("3.99");
        assertThat(d.fee(DeliveryOption.STANDARD, new BigDecimal("50.00"), null)).isEqualByComparingTo("0");
        assertThat(d.fee(DeliveryOption.EXPRESS, new BigDecimal("80.00"), null)).isEqualByComparingTo("5.99");
        assertThat(d.fee(DeliveryOption.EXPRESS, BigDecimal.ZERO, null)).isEqualByComparingTo("0");
    }
}
