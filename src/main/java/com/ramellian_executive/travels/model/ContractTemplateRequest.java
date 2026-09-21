package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotBlank;

public record ContractTemplateRequest(@NotBlank String name, @NotBlank String content) {
}
