package com.ramellian_executive.travels.model;

import java.util.Set;

public record UserRolesAndPermissionsUpdateRequest(
        Set<String> roles,
        Set<String> permissions
) {
}
