package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query("select c from CartItem c join fetch c.product p left join fetch p.category left join fetch p.seller "
            + "where c.user.id = :userId order by c.id")
    List<CartItem> findAllForUser(@Param("userId") Long userId);

    List<CartItem> findByUserIdOrderById(Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    @Modifying
    @Query("delete from CartItem c where c.user.id = :userId")
    void deleteAllForUser(@Param("userId") Long userId);

    @Query("select coalesce(sum(c.quantity), 0) from CartItem c where c.user.id = :userId")
    long countUnitsForUser(@Param("userId") Long userId);
}
