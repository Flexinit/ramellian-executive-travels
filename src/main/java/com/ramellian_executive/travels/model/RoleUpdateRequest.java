package com.ramellian_executive.travels.model;

import java.util.Set;

public record RoleUpdateRequest(
        String description,
        Set<String> permissions
) {
}
