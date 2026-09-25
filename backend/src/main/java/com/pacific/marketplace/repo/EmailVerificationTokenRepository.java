package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.EmailVerificationToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    boolean existsByUserIdAndCreatedAtAfter(Long userId, Instant after);

    /**
     * Uses a token exactly once: returns 1 only for the caller that gets there first, and only while it is unused and
     * unexpired. Two simultaneous requests with the same link can't both succeed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailVerificationToken t set t.usedAt = :now where t.id = :id and t.usedAt is null and t.expiresAt > :now")
    int consume(@Param("id") Long id, @Param("now") Instant now);

    /** Cancels every unused link for the user (a new one was issued, or the address was confirmed). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailVerificationToken t set t.usedAt = :now where t.user.id = :userId and t.usedAt is null")
    int voidOpen(@Param("userId") Long userId, @Param("now") Instant now);
}
