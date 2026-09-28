package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record StaffRenterRequest(
        @NotBlank @Email String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        String phoneNumber
) {}
