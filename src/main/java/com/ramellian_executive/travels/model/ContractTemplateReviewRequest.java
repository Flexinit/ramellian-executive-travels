package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotBlank;

public record ContractTemplateReviewRequest(@NotBlank String status) {
}
