package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.AuditLog;
import com.ramellian_executive.travels.model.AuditLogResponse;
import com.ramellian_executive.travels.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actorEmail, UUID actorId, String actorRole, String action,
                       String resourceType, String resourceId, String details,
                       String ipAddress, String status) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setActorEmail(actorEmail);
            auditLog.setActorId(actorId);
            auditLog.setActorRole(actorRole);
            auditLog.setAction(action != null ? action.toUpperCase() : "UNKNOWN");
            auditLog.setResourceType(resourceType);
            auditLog.setResourceId(resourceId);
            auditLog.setDetails(details != null && details.length() > 2900 ? details.substring(0, 2900) + "..." : details);
            auditLog.setIpAddress(ipAddress);
            auditLog.setStatus(status != null ? status : "SUCCESS");
            auditLogRepository.save(auditLog);
            log.info("######Audit log recorded successfully: {}", auditLog);
        } catch (Exception ex) {
            log.warn("Failed to persist audit log: {}", ex.getMessage());
        }
    }

    public void logAction(Authentication authentication, String action, String resourceType,
                          String resourceId, String details, String status) {
        String actorEmail = null;
        String actorRole = null;
        if (authentication != null && authentication.isAuthenticated()) {
            actorEmail = authentication.getName();
            actorRole = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(","));
        }
        record(actorEmail, null, actorRole, action, resourceType, resourceId, details, null, status);
    }

    public void logAction(String actorEmail, String action, String resourceType,
                          String resourceId, String details, String status) {
        record(actorEmail, null, null, action, resourceType, resourceId, details, null, status);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogs(String action, String actorEmail, String resourceType, Pageable pageable) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (action != null && !action.isBlank()) {
                predicates.add(cb.like(cb.upper(root.get("action")), "%" + action.trim().toUpperCase() + "%"));
            }
            if (actorEmail != null && !actorEmail.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("actorEmail")), "%" + actorEmail.trim().toLowerCase() + "%"));
            }
            if (resourceType != null && !resourceType.isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("resourceType")), resourceType.trim().toUpperCase()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageable).map(AuditLogResponse::from);
    }

    @Transactional(readOnly = true)
    public AuditLogResponse getAuditLogById(UUID id) {
        return auditLogRepository.findById(id)
                .map(AuditLogResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audit log not found"));
    }
}
