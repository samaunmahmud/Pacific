package com.pacific.marketplace.service;

import com.pacific.marketplace.config.AppProperties;
import com.pacific.marketplace.repo.OrderItemRepository;
import com.pacific.marketplace.repo.OrderRepository;
import com.pacific.marketplace.web.ApiException;
import com.pacific.marketplace.web.dto.SellerDtos.DailySales;
import com.pacific.marketplace.web.dto.SellerDtos.PeriodTotals;
import com.pacific.marketplace.web.dto.SellerDtos.SellerInsights;
import com.pacific.marketplace.web.dto.SellerDtos.TopProduct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sales chart on Seller Central: takings per day over the last 7, 30 or 90 days (in the shop's time zone), the
 * same totals for the span before so each figure can show its change, and the products that brought in the most.
 * Counts the same orders as the dashboard's "Sales": everything except cancelled and unpaid card orders.
 */
@Service
public class SellerInsightsService {

    public static final Set<Integer> SPANS = Set.of(7, 30, 90);
    static final int TOP_PRODUCTS = 5;

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final Clock clock;
    private final ZoneId zone;

    @Autowired
    public SellerInsightsService(OrderRepository orders, OrderItemRepository orderItems, AppProperties props) {
        this(orders, orderItems, props, Clock.systemUTC());
    }

    /** For tests: a fixed "now". */
    public SellerInsightsService(OrderRepository orders, OrderItemRepository orderItems, AppProperties props, Clock clock) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.clock = clock;
        this.zone = ZoneId.of(props.shop().timeZone() == null ? "Europe/London" : props.shop().timeZone());
    }

    @Transactional(readOnly = true)
    public SellerInsights insights(Long sellerId, int days) {
        if (!SPANS.contains(days)) throw ApiException.badRequest("days must be 7, 30 or 90");
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate first = today.minusDays(days - 1L); // the current span, today included
        LocalDate previousFirst = first.minusDays(days);
        Instant currentStart = first.atStartOfDay(zone).toInstant();
        Instant since = previousFirst.atStartOfDay(zone).toInstant();

        Map<LocalDate, BigDecimal> sales = new LinkedHashMap<>();
        Map<LocalDate, Long> count = new LinkedHashMap<>();
        for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
            sales.put(d, BigDecimal.ZERO);
            count.put(d, 0L);
        }
        BigDecimal previousSales = BigDecimal.ZERO;
        long previousOrders = 0;
        for (Object[] row : orders.salesSince(sellerId, since)) {
            Instant at = (Instant) row[0];
            BigDecimal total = (BigDecimal) row[1];
            if (at.isBefore(currentStart)) {
                previousSales = previousSales.add(total);
                previousOrders++;
            } else {
                LocalDate day = at.atZone(zone).toLocalDate();
                if (!sales.containsKey(day)) continue; // stamped after "today" (clock skew): leave it out
                sales.merge(day, total, BigDecimal::add);
                count.merge(day, 1L, Long::sum);
            }
        }

        long units = 0;
        long previousUnits = 0;
        Map<Long, TopProduct> byProduct = new LinkedHashMap<>();
        for (Object[] row : orderItems.linesSince(sellerId, since)) {
            Instant at = (Instant) row[0];
            int quantity = ((Number) row[3]).intValue();
            if (at.isBefore(currentStart)) {
                previousUnits += quantity;
                continue;
            }
            units += quantity;
            Long productId = (Long) row[1];
            BigDecimal revenue = ((BigDecimal) row[4]).multiply(BigDecimal.valueOf(quantity));
            byProduct.merge(productId, new TopProduct(productId, (String) row[2], quantity, revenue),
                    (a, b) -> new TopProduct(a.productId(), a.name(), a.units() + b.units(), a.revenue().add(b.revenue())));
        }

        List<DailySales> daily = new ArrayList<>();
        sales.forEach((d, s) -> daily.add(new DailySales(d, s, count.get(d))));
        BigDecimal currentSales = daily.stream().map(DailySales::sales).reduce(BigDecimal.ZERO, BigDecimal::add);
        long currentOrders = daily.stream().mapToLong(DailySales::orders).sum();
        List<TopProduct> top = byProduct.values().stream()
                .sorted(Comparator.comparing(TopProduct::revenue).reversed().thenComparing(TopProduct::name))
                .limit(TOP_PRODUCTS)
                .toList();
        return new SellerInsights(days, daily, totals(currentSales, currentOrders, units),
                totals(previousSales, previousOrders, previousUnits), top);
    }

    private static PeriodTotals totals(BigDecimal sales, long orders, long units) {
        return new PeriodTotals(sales, orders, units,
                orders == 0 ? null : sales.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP));
    }
}
