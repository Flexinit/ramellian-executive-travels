package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TwoFactorRequest(
            boolean enabled,
            @NotBlank @Pattern(regexp = "EMAIL|WHATSAPP")
            String method) {
    }