package com.pacific.marketplace.repo;

import com.pacific.marketplace.domain.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, String> {
}
