package com.ramellian_executive.travels.model;

public record WhatsAppVerificationRequest(
        String email,
        String phoneNumber
) {
}
