package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.Permission;

import java.time.ZonedDateTime;
import java.util.UUID;

public record PermissionResponse(
        UUID id,
        String permissionName,
        String description,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getPermissionName(),
                permission.getDescription(),
                permission.getCreatedAt(),
                permission.getUpdatedAt()
        );
    }
}
