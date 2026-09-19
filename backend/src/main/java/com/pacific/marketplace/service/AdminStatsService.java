package com.pacific.marketplace.service;

import com.pacific.marketplace.domain.LedgerType;
import com.pacific.marketplace.domain.OrderStatus;
import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.ReviewStatus;
import com.pacific.marketplace.domain.SellerStatus;
import com.pacific.marketplace.domain.Role;
import com.pacific.marketplace.repo.LedgerEntryRepository;
import com.pacific.marketplace.repo.OrderRepository;
import com.pacific.marketplace.repo.ProductRepository;
import com.pacific.marketplace.repo.ReviewRepository;
import com.pacific.marketplace.repo.SellerProfileRepository;
import com.pacific.marketplace.repo.UserRepository;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStatsService {

    private static final int LOW_STOCK_THRESHOLD = 5;

    private final ReviewRepository reviews;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final UserRepository users;
    private final SellerProfileRepository sellers;
    private final LedgerEntryRepository ledger;

    public AdminStatsService(ReviewRepository reviews, ProductRepository products, OrderRepository orders,
                             UserRepository users, SellerProfileRepository sellers, LedgerEntryRepository ledger) {
        this.reviews = reviews;
        this.products = products;
        this.orders = orders;
        this.users = users;
        this.sellers = sellers;
        this.ledger = ledger;
    }

    public record ReviewDistribution(long total, long flagged, long fiveStar, long other) {
    }

    public record ProductRating(Long productId, String name, double average, int reviewCount) {
    }

    public record LowStockProduct(Long productId, String name, int stock) {
    }

    public record Stats(ReviewDistribution reviews, List<ProductRating> productRatings, BigDecimal revenue,
                        long orderCount, Map<OrderStatus, Long> ordersByStatus, long customers, long activeProducts,
                        List<LowStockProduct> lowStock, int lowStockThreshold, BigDecimal commissionEarned,
                        long pendingSellers, long approvedSellers) {
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        long total = reviews.count();
        long flagged = reviews.countByStatus(ReviewStatus.FLAGGED);
        long fiveStar = reviews.countByRating(5);
        // Same buckets as the old pie chart: flagged / 5 stars / everything else
        ReviewDistribution distribution = new ReviewDistribution(total, flagged, fiveStar,
                Math.max(0, total - flagged - fiveStar));

        List<ProductRating> ratings = products.findAllByOrderByRatingAvgDescRatingCountDescNameAsc().stream()
                .map(p -> new ProductRating(p.getId(), p.getName(), p.getRatingAvg().doubleValue(),
                        p.getRatingCount()))
                .toList();

        Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
        long orderCount = 0;
        for (OrderStatus s : OrderStatus.values()) {
            long n = orders.countByStatus(s);
            byStatus.put(s, n);
            orderCount += n;
        }

        List<LowStockProduct> low = products
                .findByActiveTrueAndSellerIsNullAndStockLessThanEqualOrderByStockAscNameAsc(LOW_STOCK_THRESHOLD).stream()
                .map((Product p) -> new LowStockProduct(p.getId(), p.getName(), p.getStock()))
                .toList();

        return new Stats(distribution, ratings, orders.totalRevenue(), orderCount, byStatus,
                users.countByRole(Role.CUSTOMER), products.countByActiveTrue(), low, LOW_STOCK_THRESHOLD,
                ledger.sumAllByType(LedgerType.COMMISSION).negate(), sellers.countByStatus(SellerStatus.PENDING),
                sellers.countByStatus(SellerStatus.APPROVED));
    }
}
