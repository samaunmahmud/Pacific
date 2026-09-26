package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.ProductFamily;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductFamilyRepository extends JpaRepository<ProductFamily, Long> {
}
