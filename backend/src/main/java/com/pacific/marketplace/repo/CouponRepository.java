package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.Coupon;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /** Coupons shoppers can clip and use now on these listings. */
    @Query("select c from Coupon c where c.product.id in :ids and c.active = true and c.endsAt > :now and c.used < c.budget")
    List<Coupon> findLive(@Param("ids") Collection<Long> productIds, @Param("now") Instant now);

    @Query("select count(c) > 0 from Coupon c where c.product.id = :id and c.active = true and c.endsAt > :now")
    boolean existsRunning(@Param("id") Long productId, @Param("now") Instant now);

    @EntityGraph(attributePaths = "product")
    @Query("select c from Coupon c where (c.product.seller.id = :sellerId or (:sellerId is null and c.product.seller is null)) "
            + "and c.endsAt > :since order by c.createdAt desc")
    List<Coupon> findForStore(@Param("sellerId") Long sellerId, @Param("since") Instant since);

    /** Uses one of the coupon's budget; 0 if it's run out, ended or been switched off. */
    @Modifying(flushAutomatically = true)
    @Query("update Coupon c set c.used = c.used + 1 where c.id = :id and c.used < c.budget and c.active = true and c.endsAt > :now")
    int use(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("update Coupon c set c.used = case when c.used > 0 then c.used - 1 else 0 end where c.id = :id")
    int release(@Param("id") Long id);
}
