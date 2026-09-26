package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.AuditLog;

import java.time.ZonedDateTime;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String actorEmail,
        UUID actorId,
        String actorRole,
        String action,
        String resourceType,
        String resourceId,
        String details,
        String ipAddress,
        String status,
        ZonedDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        if (log == null) return null;
        return new AuditLogResponse(
                log.getId(),
                log.getActorEmail(),
                log.getActorId(),
                log.getActorRole(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getDetails(),
                log.getIpAddress(),
                log.getStatus(),
                log.getCreatedAt()
        );
    }
}
