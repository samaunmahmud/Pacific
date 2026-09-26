package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.OrderItem;
import com.pacific.marketplace.domain.Product;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /** Purchases of a product from any seller: {@code productId} is its catalog page. */
    @Query("select count(oi) from OrderItem oi where oi.order.user.id = :userId "
            + "and (oi.product.id = :productId or oi.product.groupId = :productId) "
            + "and oi.order.status not in (com.pacific.marketplace.domain.OrderStatus.CANCELLED, com.pacific.marketplace.domain.OrderStatus.AWAITING_PAYMENT)")
    long countPurchases(@Param("userId") Long userId, @Param("productId") Long productId);

    /** Which of these users have bought (and not cancelled) the product, from any seller. */
    @Query("select distinct o.user.id from OrderItem oi join oi.order o "
            + "where (oi.product.id = :productId or oi.product.groupId = :productId) "
            + "and o.user.id in :userIds and o.status not in (com.pacific.marketplace.domain.OrderStatus.CANCELLED, com.pacific.marketplace.domain.OrderStatus.AWAITING_PAYMENT)")
    List<Long> findBuyerIds(@Param("productId") Long productId, @Param("userIds") java.util.Collection<Long> userIds);

    @Query("select coalesce(sum(oi.quantity), 0) from OrderItem oi where oi.order.seller.id = :sellerId "
            + "and oi.order.status not in (com.pacific.marketplace.domain.OrderStatus.CANCELLED, com.pacific.marketplace.domain.OrderStatus.AWAITING_PAYMENT)")
    long unitsSoldBySeller(@Param("sellerId") Long sellerId);

    /**
     * Products the customer has bought (and not cancelled) but not reviewed yet, most recent purchase first. Returns
     * catalog pages, where reviews live, whichever seller it was bought from.
     */
    @Query("select c from OrderItem oi join oi.product p join oi.order o, Product c "
            + "where c.id = coalesce(p.groupId, p.id) "
            + "and o.user.id = :userId and o.status not in (com.pacific.marketplace.domain.OrderStatus.CANCELLED, com.pacific.marketplace.domain.OrderStatus.AWAITING_PAYMENT) "
            + "and c.active = true "
            + "and not exists (select 1 from Review r where r.user.id = :userId and r.product.id = c.id) "
            + "group by c order by max(o.createdAt) desc")
    List<Product> findUnratedPurchasedProducts(@Param("userId") Long userId);
}
