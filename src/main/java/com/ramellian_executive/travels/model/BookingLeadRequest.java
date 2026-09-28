package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.UUID;

public record BookingLeadRequest(
    String email,
    String phoneNumber,
    @NotNull UUID vehicleId,
    @NotNull ZonedDateTime startTime,
    @NotNull ZonedDateTime endTime,
    String notes
) {}
