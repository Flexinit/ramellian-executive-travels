package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.AssertTrue;

public record ContractSignatureRequest(
        @AssertTrue(message = "Signing consent is required") boolean consent) {
}
