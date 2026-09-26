package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.PromoRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromoRedemptionRepository extends JpaRepository<PromoRedemption, Long> {

    boolean existsByPromoIdAndUserId(Long promoId, Long userId);

    /** The order was cancelled: the customer can use the code again. Returns how many were removed. */
    @Modifying(flushAutomatically = true)
    @Query("delete from PromoRedemption r where r.promo.id = :promoId and r.orderId = :orderId")
    int deleteForOrder(@Param("promoId") Long promoId, @Param("orderId") Long orderId);
}
