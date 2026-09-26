package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotBlank;

public record PermissionRequest(
        @NotBlank(message = "Permission name is required") String permissionName,
        String description
) {
}
