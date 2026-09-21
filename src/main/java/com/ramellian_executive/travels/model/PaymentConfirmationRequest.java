package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotBlank;

public record PaymentConfirmationRequest(@NotBlank String paymentMethod,
                                         @NotBlank String transactionReference) {
}
