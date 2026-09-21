package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.AssertTrue;

public record ContractAcceptanceRequest(
        @AssertTrue(message = "Contract acceptance is required") boolean accepted) {
}
