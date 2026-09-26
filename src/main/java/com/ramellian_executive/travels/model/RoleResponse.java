package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.Permission;
import com.ramellian_executive.travels.entity.UserRole;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        String roleName,
        String description,
        List<String> permissions,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static RoleResponse from(UserRole role) {
        List<String> perms = role.getPermissions() != null
                ? role.getPermissions().stream().map(Permission::getPermissionName).sorted().toList()
                : List.of();
        return new RoleResponse(
                role.getId(),
                role.getRoleName(),
                role.getDescription(),
                perms,
                role.getCreatedAt(),
                role.getUpdatedAt()
        );
    }
}
