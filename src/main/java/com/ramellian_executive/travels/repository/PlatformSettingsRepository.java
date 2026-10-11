package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, UUID> {
    Optional<PlatformSettings> findFirstByOrderByCreatedAtDesc();
}
