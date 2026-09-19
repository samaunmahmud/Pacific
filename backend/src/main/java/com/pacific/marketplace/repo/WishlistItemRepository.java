package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.Product;
import com.pacific.marketplace.domain.WishlistItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    Optional<WishlistItem> findByUserIdAndProductId(Long userId, Long productId);

    @Query("select p from WishlistItem w join w.product p left join fetch p.category left join fetch p.seller "
            + "where w.user.id = :userId order by w.id desc")
    List<Product> findProductsForUser(@Param("userId") Long userId);

    @Query("select w.product.id from WishlistItem w where w.user.id = :userId")
    List<Long> findProductIdsForUser(@Param("userId") Long userId);
}
