package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 200) String email,
        String phone,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 5000) String message
) {}
