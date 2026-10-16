package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChangeVehicleRequest(
        @NotNull(message = "New vehicle ID is required") UUID vehicleId,
        String reason
) {}
