package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotBlank;

public record DocumentReviewRequest(@NotBlank String status, String notes) {
}
