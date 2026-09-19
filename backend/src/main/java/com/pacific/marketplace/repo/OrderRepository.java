package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.Order;
import com.pacific.marketplace.domain.OrderStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items", "user", "seller"})
    List<Order> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    @EntityGraph(attributePaths = {"items", "user", "seller"})
    Optional<Order> findWithItemsById(Long id);

    /** Serialises concurrent status changes/cancellations on one order so stock is restored at most once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lockById(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"user", "seller"})
    Page<Order> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "seller"})
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    /** A seller's orders, leaving out card orders that haven't been paid yet. */
    @EntityGraph(attributePaths = {"user", "seller"})
    Page<Order> findBySellerIdAndStatusNot(Long sellerId, OrderStatus excluded, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "user", "seller"})
    List<Order> findByCheckoutRef(String checkoutRef);

    @EntityGraph(attributePaths = {"user", "seller"})
    Page<Order> findBySellerIdAndStatus(Long sellerId, OrderStatus status, Pageable pageable);

    boolean existsByUserIdAndSellerIdAndStatus(Long userId, Long sellerId, OrderStatus status);

    long countBySellerIdAndStatus(Long sellerId, OrderStatus status);

    @Query("select coalesce(sum(o.total), 0) from Order o where o.seller.id = :sellerId "
            + "and o.status not in (com.pacific.marketplace.domain.OrderStatus.CANCELLED, com.pacific.marketplace.domain.OrderStatus.AWAITING_PAYMENT)")
    BigDecimal grossSalesForSeller(@Param("sellerId") Long sellerId);

    long countByStatus(OrderStatus status);

    @Query("select coalesce(sum(o.total), 0) from Order o where o.status not in (com.pacific.marketplace.domain.OrderStatus.CANCELLED, com.pacific.marketplace.domain.OrderStatus.AWAITING_PAYMENT)")
    BigDecimal totalRevenue();
}
