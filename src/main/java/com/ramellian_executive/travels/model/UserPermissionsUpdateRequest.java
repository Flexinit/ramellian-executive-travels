package com.ramellian_executive.travels.model;

import java.util.Set;

public record UserPermissionsUpdateRequest(
        Set<String> permissions
) {
}
