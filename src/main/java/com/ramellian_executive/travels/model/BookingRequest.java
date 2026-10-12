package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.TripType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.ZonedDateTime;
import java.util.UUID;

public record BookingRequest(@NotNull UUID vehicleId,
                             @NotNull @Future ZonedDateTime startTime,
                             @NotNull @Future ZonedDateTime endTime,
                             @NotBlank String bookingMode,
                             TripType tripType,
                             @Size(max = 1000) String renterNotes,
                             UUID renterId,
                             @Size(max = 500) String pickupLocation,
                             @Size(max = 500) String dropoffLocation,
                             Boolean deliveryRequested,
                             Integer passengerCount) {
    public BookingRequest(UUID vehicleId, ZonedDateTime startTime, ZonedDateTime endTime) {
        this(vehicleId, startTime, endTime, "SELF_DRIVE", null, null, null, null, null, null, null);
    }

    public BookingRequest(UUID vehicleId, ZonedDateTime startTime, ZonedDateTime endTime,
                          String bookingMode, TripType tripType, String renterNotes, UUID renterId) {
        this(vehicleId, startTime, endTime, bookingMode, tripType, renterNotes, renterId, null, null, null, null);
    }
}
