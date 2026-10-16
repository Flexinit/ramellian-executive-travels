package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyCodeRequest(
        String email,
        String phoneNumber,
        @NotBlank @Size(min = 6, max = 6) String code,
        Boolean rememberMe
) {
    public VerifyCodeRequest(String email, String code) {
        this(email, null, code, null);
    }
}