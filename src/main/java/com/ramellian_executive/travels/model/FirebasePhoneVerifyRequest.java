package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotBlank;

public record FirebasePhoneVerifyRequest(
        String email,
        @NotBlank(message = "Firebase ID token is required") String firebaseIdToken
) {}
