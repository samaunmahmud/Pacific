package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.PromoCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {

    @EntityGraph(attributePaths = "seller")
    Optional<PromoCode> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("select p from PromoCode p where (p.seller.id = :sellerId or (:sellerId is null and p.seller is null)) "
            + "and p.endsAt > :since order by p.createdAt desc")
    List<PromoCode> findForStore(@Param("sellerId") Long sellerId, @Param("since") Instant since);

    /** Uses the code once; 0 if it's used up, ended or switched off. */
    @Modifying(flushAutomatically = true)
    @Query("update PromoCode p set p.used = p.used + 1 where p.id = :id and p.active = true and p.endsAt > :now "
            + "and (p.maxUses is null or p.used < p.maxUses)")
    int use(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("update PromoCode p set p.used = case when p.used > 0 then p.used - 1 else 0 end where p.id = :id")
    int release(@Param("id") Long id);
}
