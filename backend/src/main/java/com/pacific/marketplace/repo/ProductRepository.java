package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.Product;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @Override
    @EntityGraph(attributePaths = {"category", "seller"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "seller"})
    Optional<Product> findWithCategoryById(Long id);

    @EntityGraph(attributePaths = {"category", "seller"})
    List<Product> findWithCategoryByIdIn(Collection<Long> ids);

    /**
     * Atomically takes stock. Returns 0 if the product is inactive or has less than {@code quantity} left,
     * so concurrent checkouts can never oversell.
     */
    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock - :quantity "
            + "where p.id = :id and p.active = true and p.stock >= :quantity "
            + "and (p.seller is null or p.seller.id in (select s.id from SellerProfile s "
            + "where s.status = com.pacific.marketplace.domain.SellerStatus.APPROVED))")
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :quantity where p.id = :id")
    int incrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    /** Pacific's own (house) products running low. */
    @EntityGraph(attributePaths = "category")
    List<Product> findByActiveTrueAndSellerIsNullAndStockLessThanEqualOrderByStockAscNameAsc(int stock);

    List<Product> findBySellerIdAndActiveTrueAndStockLessThanEqualOrderByStockAscNameAsc(Long sellerId, int stock);

    @EntityGraph(attributePaths = {"category", "seller"})
    List<Product> findAllByOrderByRatingAvgDescRatingCountDescNameAsc();

    long countBySellerId(Long sellerId);

    long countByActiveTrue();
}
