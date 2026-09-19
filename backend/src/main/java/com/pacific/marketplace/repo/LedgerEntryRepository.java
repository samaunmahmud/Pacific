package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.LedgerEntry;
import com.pacific.marketplace.domain.LedgerType;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Page<LedgerEntry> findBySellerId(Long sellerId, Pageable pageable);

    boolean existsByOrderIdAndType(Long orderId, LedgerType type);

    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.seller.id = :sellerId")
    BigDecimal balance(@Param("sellerId") Long sellerId);

    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.seller.id = :sellerId and e.type = :type")
    BigDecimal sumByType(@Param("sellerId") Long sellerId, @Param("type") LedgerType type);

    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.type = :type")
    BigDecimal sumAllByType(@Param("type") LedgerType type);
}
