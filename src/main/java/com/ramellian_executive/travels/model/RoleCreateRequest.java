package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotBlank;
import java.util.Set;

public record RoleCreateRequest(
        @NotBlank(message = "Role name is required") String roleName,
        String description,
        Set<String> permissions
) {
}
