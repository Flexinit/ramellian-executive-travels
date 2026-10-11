package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

public record PostponementRequest(
        @NotNull ZonedDateTime startTime,
        @NotNull ZonedDateTime endTime
) {}
