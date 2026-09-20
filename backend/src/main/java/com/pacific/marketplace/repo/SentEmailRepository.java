package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.SentEmail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SentEmailRepository extends JpaRepository<SentEmail, Long> {
}
