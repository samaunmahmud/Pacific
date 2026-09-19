package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.SellerProfile;
import com.pacific.marketplace.domain.SellerStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {

    Optional<SellerProfile> findByUserId(Long userId);

    Optional<SellerProfile> findBySlug(String slug);

    boolean existsByStoreNameIgnoreCase(String storeName);

    boolean existsBySlug(String slug);

    /** Serialises payouts (and other balance-sensitive changes) for one seller. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SellerProfile s where s.id = :id")
    Optional<SellerProfile> lockById(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<SellerProfile> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<SellerProfile> findByStatus(SellerStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<SellerProfile> findWithUserById(Long id);

    long countByStatus(SellerStatus status);
}
