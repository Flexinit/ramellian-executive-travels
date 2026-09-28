package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.UUID;

public record BookingQuoteRequest(
    @NotNull UUID vehicleId,
    @NotNull ZonedDateTime startTime,
    @NotNull ZonedDateTime endTime
) {}
