package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.LightningDeal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LightningDealRepository extends JpaRepository<LightningDeal, Long> {

    /** Deals running now on these listings (sold out or not), soonest ending first. */
    @Query("select d from LightningDeal d where d.product.id in :ids and d.startsAt <= :now and d.endsAt > :now "
            + "order by d.endsAt")
    List<LightningDeal> findLive(@Param("ids") Collection<Long> productIds, @Param("now") Instant now);

    /** Would a deal on this listing overlap another one? */
    @Query("select count(d) > 0 from LightningDeal d where d.product.id = :id and d.startsAt < :end and d.endsAt > :start")
    boolean overlaps(@Param("id") Long productId, @Param("start") Instant start, @Param("end") Instant end);

    @EntityGraph(attributePaths = "product")
    @Query("select d from LightningDeal d where (d.product.seller.id = :sellerId or (:sellerId is null and d.product.seller is null)) "
            + "and d.endsAt > :since order by d.startsAt desc")
    List<LightningDeal> findForStore(@Param("sellerId") Long sellerId, @Param("since") Instant since);

    /** Deals that started or ended in (from, to]: their product pages need a new buy box. */
    @Query("select distinct d.product.id from LightningDeal d where (d.startsAt > :from and d.startsAt <= :to) "
            + "or (d.endsAt > :from and d.endsAt <= :to)")
    List<Long> findProductsWithWindowChange(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Takes units at the deal price. Returns 0 if the deal isn't running or hasn't that many left, so two shoppers can
     * never both get the last one.
     */
    @Modifying(flushAutomatically = true)
    @Query("update LightningDeal d set d.claimed = d.claimed + :qty where d.id = :id and d.claimed + :qty <= d.quantity "
            + "and d.startsAt <= :now and d.endsAt > :now")
    int claim(@Param("id") Long id, @Param("qty") int quantity, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("update LightningDeal d set d.claimed = case when d.claimed >= :qty then d.claimed - :qty else 0 end where d.id = :id")
    int release(@Param("id") Long id, @Param("qty") int quantity);
}
