package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BookingPolicyRequest(
        @NotNull @Min(0) Integer freeCancellationWindowHours,
        @NotNull @Min(0) Integer cancellationDeadlineHours,
        @NotNull @Min(0) @Max(100) Integer lateCancellationRefundPct,
        @NotNull Boolean postponementAllowed,
        @NotNull @Min(0) Integer postponementDeadlineHours,
        @NotNull @Min(0) Integer maxPostponements
) {}
