package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, UUID> {
    Optional<ContractTemplate> findFirstByOwnerIsNullAndStatusOrderByVersionDesc(ContractTemplateStatus status);
    Optional<ContractTemplate> findFirstByOwnerIdAndStatusOrderByVersionDesc(UUID ownerId, ContractTemplateStatus status);
    List<ContractTemplate> findAllByOrderByCreatedAtDesc();
    List<ContractTemplate> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
