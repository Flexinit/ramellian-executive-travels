package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.*;

public record ReviewRequest(@NotNull @DecimalMin("1.0") @DecimalMax("5.0") Double rating,
                            @Size(max = 2000) String feedback,
                            @Size(max = 2000) String complaint) {
}
