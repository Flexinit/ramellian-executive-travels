package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.*;

public record ReviewRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @NotNull Boolean customerSatisfied,
        @Size(max = 2000) String feedback,
        @Size(max = 2000) String complaint) {
}
