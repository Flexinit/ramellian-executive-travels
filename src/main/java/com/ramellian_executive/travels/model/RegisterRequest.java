package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        String phoneNumber,
        String verificationChannel,
        String role
) {
    public RegisterRequest(String email, String password, String firstName, String lastName) {
        this(email, password, firstName, lastName, null, null, null);
    }

    public RegisterRequest(String email, String password, String firstName, String lastName, String phoneNumber) {
        this(email, password, firstName, lastName, phoneNumber, null, null);
    }
}