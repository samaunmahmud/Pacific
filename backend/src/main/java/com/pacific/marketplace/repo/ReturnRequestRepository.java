package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.ReturnRequest;
import com.pacific.marketplace.domain.ReturnStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {

    /** The order's id without loading the return, so the order can be locked before the return is read. */
    @Query("select r.order.id from ReturnRequest r where r.id = :id")
    Optional<Long> findOrderIdById(@Param("id") Long id);

    Page<ReturnRequest> findByOrderSellerId(Long sellerId, Pageable pageable);

    Page<ReturnRequest> findByOrderSellerIdAndStatus(Long sellerId, ReturnStatus status, Pageable pageable);

    Page<ReturnRequest> findByStatus(ReturnStatus status, Pageable pageable);

    long countByOrderSellerIdAndStatus(Long sellerId, ReturnStatus status);

    long countByStatus(ReturnStatus status);
}
