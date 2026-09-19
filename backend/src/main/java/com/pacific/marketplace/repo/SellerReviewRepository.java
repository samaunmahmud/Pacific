package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.SellerReview;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SellerReviewRepository extends JpaRepository<SellerReview, Long> {

    @EntityGraph(attributePaths = "user")
    List<SellerReview> findTop50BySellerIdOrderByCreatedAtDescIdDesc(Long sellerId);

    Optional<SellerReview> findBySellerIdAndUserId(Long sellerId, Long userId);

    @Query("select new com.pacific.marketplace.repo.RatingStats(avg(r.rating), count(r)) from SellerReview r "
            + "where r.seller.id = :sellerId")
    RatingStats stats(@Param("sellerId") Long sellerId);

    @Query("select r.rating, count(r) from SellerReview r where r.seller.id = :sellerId group by r.rating")
    List<Object[]> distribution(@Param("sellerId") Long sellerId);
}
