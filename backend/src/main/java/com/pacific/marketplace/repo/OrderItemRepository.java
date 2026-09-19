package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.OrderItem;
import com.pacific.marketplace.domain.Product;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("select count(oi) from OrderItem oi where oi.order.user.id = :userId and oi.product.id = :productId "
            + "and oi.order.status <> com.pacific.marketplace.domain.OrderStatus.CANCELLED")
    long countPurchases(@Param("userId") Long userId, @Param("productId") Long productId);

    /** Which of these users have bought (and not cancelled) the product. */
    @Query("select distinct o.user.id from OrderItem oi join oi.order o where oi.product.id = :productId "
            + "and o.user.id in :userIds and o.status <> com.pacific.marketplace.domain.OrderStatus.CANCELLED")
    List<Long> findBuyerIds(@Param("productId") Long productId, @Param("userIds") java.util.Collection<Long> userIds);

    @Query("select coalesce(sum(oi.quantity), 0) from OrderItem oi where oi.order.seller.id = :sellerId "
            + "and oi.order.status <> com.pacific.marketplace.domain.OrderStatus.CANCELLED")
    long unitsSoldBySeller(@Param("sellerId") Long sellerId);

    /** Products the customer has bought (and not cancelled) but not reviewed yet, most recent purchase first. */
    @Query("select p from OrderItem oi join oi.product p join oi.order o "
            + "where o.user.id = :userId and o.status <> com.pacific.marketplace.domain.OrderStatus.CANCELLED "
            + "and p.active = true "
            + "and not exists (select 1 from Review r where r.user.id = :userId and r.product.id = p.id) "
            + "group by p order by max(o.createdAt) desc")
    List<Product> findUnratedPurchasedProducts(@Param("userId") Long userId);
}
