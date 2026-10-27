package com.ramellian_executive.travels.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SplitPaymentRequest(
        @NotEmpty @Valid List<ContributorRequest> contributors,
        /** Minutes before the split group expires. Default 30. */
        Integer expiryMinutes
) {
    public record ContributorRequest(
            @NotBlank String payerPhone,
            String payerName,
            String payerEmail,
            @NotNull @DecimalMin("1.0") Double amount
    ) {}
}
