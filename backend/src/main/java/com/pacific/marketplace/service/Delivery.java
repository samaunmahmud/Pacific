package com.pacific.marketplace.service;

import com.pacific.marketplace.config.AppProperties;
import com.pacific.marketplace.domain.DeliveryOption;
import com.pacific.marketplace.domain.SellerProfile;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Delivery prices and dates. Each seller ships their part of an order: standard delivery is free once that part
 * reaches the seller's threshold (or the shop's), express costs a fixed amount. Dates count business days (weekends
 * are skipped): an order placed before the cut-off on a business day counts from that day, then the seller's
 * dispatch time, then the transit time.
 */
@Component
public class Delivery {

    /** The days a delivery may arrive (equal for express). */
    public record Window(LocalDate from, LocalDate to) {
    }

    private final AppProperties.Shop shop;
    private final Clock clock;
    private final ZoneId zone;

    @Autowired
    public Delivery(AppProperties props) {
        this(props, Clock.systemUTC());
    }

    /** For tests: a fixed "now". */
    public Delivery(AppProperties props, Clock clock) {
        this.shop = props.shop();
        this.clock = clock;
        this.zone = ZoneId.of(shop.timeZone() == null ? "Europe/London" : shop.timeZone());
    }

    public static final int DEFAULT_DISPATCH_DAYS = 1;

    /** The free-delivery threshold for a seller's orders (null seller = Pacific's own store). */
    public BigDecimal freeThreshold(SellerProfile seller) {
        return seller != null && seller.getFreeDeliveryThreshold() != null
                ? seller.getFreeDeliveryThreshold() : shop.freeShippingThreshold();
    }

    public int dispatchDays(SellerProfile seller) {
        return seller == null ? DEFAULT_DISPATCH_DAYS : seller.getDispatchDays();
    }

    /** What delivering a seller's part of an order costs. Nothing to ship, nothing to pay. */
    public BigDecimal fee(DeliveryOption option, BigDecimal subtotal, SellerProfile seller) {
        if (subtotal.signum() == 0) return BigDecimal.ZERO.setScale(2);
        if (option == DeliveryOption.EXPRESS) return shop.expressRate().setScale(2);
        return subtotal.compareTo(freeThreshold(seller)) >= 0 ? BigDecimal.ZERO.setScale(2) : shop.shippingFlatRate().setScale(2);
    }

    /** When an order placed now would arrive. */
    public Window window(DeliveryOption option, SellerProfile seller) {
        LocalDate dispatch = addBusinessDays(firstWorkingDay(), dispatchDays(seller));
        return option == DeliveryOption.EXPRESS
                ? new Window(addBusinessDays(dispatch, shop.expressDays()), addBusinessDays(dispatch, shop.expressDays()))
                : new Window(addBusinessDays(dispatch, shop.standardDaysMin()), addBusinessDays(dispatch, shop.standardDaysMax()));
    }

    /** Today's cut-off, if orders placed now still count from today; null otherwise ("Order within 3 hrs"). */
    public Instant orderWithin() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
        ZonedDateTime cutoff = now.toLocalDate().atTime(shop.dispatchCutoffHour(), 0).atZone(zone);
        return isBusinessDay(now.toLocalDate()) && now.isBefore(cutoff) ? cutoff.toInstant() : null;
    }

    /** The day an order placed now starts counting from. */
    private LocalDate firstWorkingDay() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
        LocalDate day = now.toLocalDate();
        if (!isBusinessDay(day) || now.getHour() >= shop.dispatchCutoffHour()) day = addBusinessDays(day, 1);
        return day;
    }

    static LocalDate addBusinessDays(LocalDate day, int days) {
        LocalDate d = day;
        int left = days;
        while (left > 0) {
            d = d.plusDays(1);
            if (isBusinessDay(d)) left--;
        }
        return d;
    }

    private static boolean isBusinessDay(LocalDate d) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
