package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.Size;

public record OwnerProfileRequest(
        @Size(max = 50) String ownerType,
        @Size(max = 255) String businessName,
        @Size(max = 100) String businessRegistrationNumber,
        @Size(max = 100) String businessTaxNumber,
        @Size(max = 255) String businessAddress,
        @Size(max = 100) String payoutProvider,
        @Size(max = 100) String payoutAccountReference) {
}
