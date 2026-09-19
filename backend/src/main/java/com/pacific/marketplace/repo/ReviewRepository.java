package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.Review;
import com.pacific.marketplace.domain.ReviewStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long>, JpaSpecificationExecutor<Review> {

    @Override
    @EntityGraph(attributePaths = {"user", "product"})
    Page<Review> findAll(Specification<Review> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "product"})
    Optional<Review> findWithUserAndProductById(Long id);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    /** Reviews shown on a product page: everything visible, plus the viewer's own review even if flagged. */
    @Query("select r from Review r join fetch r.user where r.product.id = :productId "
            + "and (r.status = com.pacific.marketplace.domain.ReviewStatus.VISIBLE or r.user.id = :viewerId)")
    List<Review> findForProductPage(@Param("productId") Long productId, @Param("viewerId") Long viewerId, Sort sort);

    @Query("select r from Review r join fetch r.user join fetch r.product p "
            + "where r.user.id = :userId and (lower(r.comment) like :q or lower(coalesce(r.title, '')) like :q "
            + "or lower(p.name) like :q)")
    List<Review> findMine(@Param("userId") Long userId, @Param("q") String q, Sort sort);

    @Query("select new com.pacific.marketplace.repo.RatingStats(avg(r.rating), count(r)) from Review r "
            + "where r.product.id = :productId and r.status = com.pacific.marketplace.domain.ReviewStatus.VISIBLE")
    RatingStats visibleStats(@Param("productId") Long productId);

    @Query("select r.rating, count(r) from Review r where r.product.id = :productId "
            + "and r.status = com.pacific.marketplace.domain.ReviewStatus.VISIBLE group by r.rating")
    List<Object[]> ratingDistribution(@Param("productId") Long productId);

    long countByStatus(ReviewStatus status);

    long countByRating(int rating);
}
