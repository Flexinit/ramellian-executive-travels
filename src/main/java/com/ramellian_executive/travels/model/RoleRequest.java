package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotBlank;

public record RoleRequest(@NotBlank String role) {
    }