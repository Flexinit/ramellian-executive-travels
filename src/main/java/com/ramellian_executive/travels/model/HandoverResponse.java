package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.FuelLevel;
import com.ramellian_executive.travels.entity.HandoverType;
import com.ramellian_executive.travels.entity.VehicleHandover;

import java.time.ZonedDateTime;
import java.util.UUID;

public record HandoverResponse(
        UUID id,
        UUID bookingId,
        String vehicleRegistration,
        String vehicleModel,
        String renterEmail,
        HandoverType type,
        ZonedDateTime handoverTime,
        Integer odometerReading,
        FuelLevel fuelLevel,
        String conditionNotes,
        boolean damageNoted,
        String damageDescription,
        String location,
        String signedBy,
        ZonedDateTime signedAt,
        String bookingStatus
) {
    public static HandoverResponse from(VehicleHandover h) {
        return new HandoverResponse(
                h.getId(),
                h.getBooking().getId(),
                h.getBooking().getVehicle().getRegistrationNumber(),
                h.getBooking().getVehicle().getModel(),
                h.getBooking().getUser().getEmailAddress(),
                h.getType(),
                h.getHandoverTime(),
                h.getOdometerReading(),
                h.getFuelLevel(),
                h.getConditionNotes(),
                h.isDamageNoted(),
                h.getDamageDescription(),
                h.getLocation(),
                h.getSignedBy(),
                h.getSignedAt(),
                h.getBooking().getStatus().name()
        );
    }
}
