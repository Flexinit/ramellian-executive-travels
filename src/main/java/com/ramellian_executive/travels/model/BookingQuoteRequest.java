package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.BookingMode;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.UUID;

public record BookingQuoteRequest(
    @NotNull UUID vehicleId,
    @NotNull ZonedDateTime startTime,
    @NotNull ZonedDateTime endTime,
    Boolean deliveryRequested,
    BookingMode bookingMode
) {
    public BookingQuoteRequest(UUID vehicleId, ZonedDateTime startTime, ZonedDateTime endTime) {
        this(vehicleId, startTime, endTime, null, null);
    }

    public BookingQuoteRequest(UUID vehicleId, ZonedDateTime startTime, ZonedDateTime endTime,
                               Boolean deliveryRequested) {
        this(vehicleId, startTime, endTime, deliveryRequested, null);
    }
}
