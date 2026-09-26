package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(
        @NotBlank @Email String email,
        String channel,
        String phoneNumber
) {
    public ResendVerificationRequest(String email) {
        this(email, null, null);
    }

    public ResendVerificationRequest(String email, String channel) {
        this(email, channel, null);
    }
}