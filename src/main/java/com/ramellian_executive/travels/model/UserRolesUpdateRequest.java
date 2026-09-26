package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record UserRolesUpdateRequest(
        @NotEmpty(message = "At least one role must be provided") Set<String> roles
) {
}
