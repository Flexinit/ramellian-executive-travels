package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotBlank;

public record OwnerProfileRequest(
        @NotBlank String ownerType,
        String businessName,
        String businessRegistrationNumber,
        String businessTaxNumber,
        String businessAddress,
        String payoutProvider,
        String payoutAccountReference) {
}
