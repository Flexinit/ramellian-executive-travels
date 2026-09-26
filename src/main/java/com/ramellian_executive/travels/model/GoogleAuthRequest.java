package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotBlank;

public record GoogleAuthRequest(
        @NotBlank String idToken,
        String role
) {}
