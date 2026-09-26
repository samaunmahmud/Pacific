package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.CouponClip;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponClipRepository extends JpaRepository<CouponClip, Long> {

    Optional<CouponClip> findByCouponIdAndUserId(Long couponId, Long userId);

    /** Of these coupons, the ones this customer has clipped and not used yet. */
    @Query("select c.coupon.id from CouponClip c where c.user.id = :userId and c.coupon.id in :ids and c.orderId is null")
    List<Long> findUnusedClips(@Param("userId") Long userId, @Param("ids") Collection<Long> couponIds);

    /** Marks the customer's clip used by an order; 0 if they never clipped it or already used it. */
    @Modifying(flushAutomatically = true)
    @Query("update CouponClip c set c.orderId = :orderId where c.coupon.id = :couponId and c.user.id = :userId and c.orderId is null")
    int redeem(@Param("couponId") Long couponId, @Param("userId") Long userId, @Param("orderId") Long orderId);

    /** The order was cancelled: the customer can use the coupon again. */
    @Modifying(flushAutomatically = true)
    @Query("update CouponClip c set c.orderId = null where c.coupon.id = :couponId and c.orderId = :orderId")
    int release(@Param("couponId") Long couponId, @Param("orderId") Long orderId);
}
