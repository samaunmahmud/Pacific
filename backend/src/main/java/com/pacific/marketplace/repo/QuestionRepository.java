package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.Question;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    @EntityGraph(attributePaths = "user")
    List<Question> findByProductIdOrderByCreatedAtDescIdDesc(Long productId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "product"})
    java.util.Optional<Question> findWithUserAndProductById(Long id);

    /** Questions on this seller's products that nobody has answered yet. */
    @Query("select q from Question q join fetch q.product p join fetch q.user "
            + "where p.seller.id = :sellerId and not exists (select 1 from Answer a where a.question = q) "
            + "order by q.createdAt desc")
    List<Question> findUnansweredForSeller(@Param("sellerId") Long sellerId, Pageable pageable);

    @Query("select count(q) from Question q where q.product.seller.id = :sellerId "
            + "and not exists (select 1 from Answer a where a.question = q)")
    long countUnansweredForSeller(@Param("sellerId") Long sellerId);
}
