package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.Role;
import com.pacific.marketplace.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    /** Serialises changes to one customer's saved addresses. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    /** Read fresh on every request, to reject sign-in tokens issued before a password change. */
    @org.springframework.data.jpa.repository.Query("select u.passwordVersion from User u where u.id = :id")
    Optional<Integer> findPasswordVersionById(@org.springframework.data.repository.query.Param("id") Long id);

    boolean existsByRole(Role role);

    long countByRole(Role role);
}
