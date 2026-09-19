package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.Payment;
import com.pacific.marketplace.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByCheckoutRef(String checkoutRef);

    /** Payment state changes (paid / cancelled / expired) are serialised per payment, so each happens once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.checkoutRef = :ref")
    Optional<Payment> lockByCheckoutRef(@Param("ref") String ref);

    @Query("select p.checkoutRef from Payment p where p.status = :status and p.expiresAt < :before")
    List<String> findRefsByStatusAndExpiresBefore(@Param("status") PaymentStatus status, @Param("before") Instant before);
}
