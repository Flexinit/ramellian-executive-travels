package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AuditLog> findByActionContainingIgnoreCaseOrderByCreatedAtDesc(String action, Pageable pageable);

    Page<AuditLog> findByActorEmailContainingIgnoreCaseOrderByCreatedAtDesc(String actorEmail, Pageable pageable);

    Page<AuditLog> findByResourceTypeIgnoreCaseOrderByCreatedAtDesc(String resourceType, Pageable pageable);
}
