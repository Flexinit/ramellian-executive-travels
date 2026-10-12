package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.TripType;
import com.ramellian_executive.travels.entity.Vehicle;
import com.ramellian_executive.travels.entity.VehicleApprovalStatus;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        String model,
        String vin,
        String registrationNumber,
        String location,
        Double pricePerDay,
        Double rating,
        boolean available,
        int capacity,
        String color,
        String fuelType,
        String transmissionType,
        boolean selfDrive,
        boolean chauffeured,
        Set<TripType> tripTypes,
        String ownerEmail,
        UUID ownerId,
        boolean hasWorkingAC,
        boolean comprehensiveInsurance,
        String insuranceVerificationStickerPhoto,
        ZonedDateTime availableFrom,
        ZonedDateTime availableTo,
        VehicleApprovalStatus approvalStatus,
        String approvalNotes,
        String reviewedBy,
        ZonedDateTime reviewedAt,
        boolean booked,
        Integer year,
        String insurer,
        String policyNumber
) {

    public static VehicleResponse from(Vehicle vehicle) {
        return from(vehicle, false);
    }

    public static VehicleResponse from(Vehicle vehicle, boolean booked) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getModel(),
                vehicle.getVIN(),
                vehicle.getRegistrationNumber(),
                vehicle.getLocation(),
                vehicle.getPricePerDay(),
                vehicle.getRating(),
                vehicle.isAvailable(),
                vehicle.getCapacity(),
                vehicle.getColor(),
                vehicle.getFuelType(),
                vehicle.getTransmissionType(),
                vehicle.isSelfDrive(),
                vehicle.isChauffeured(),
                vehicle.getTripTypes(),
                vehicle.getOwner() != null ? vehicle.getOwner().getEmailAddress() : null,
                vehicle.getOwner() != null ? vehicle.getOwner().getId() : null,
                vehicle.isHasWorkingAC(),
                vehicle.isComprehensiveInsurance(),
                vehicle.getInsuranceVerificationStickerPhoto(),
                vehicle.getAvailableFrom(),
                vehicle.getAvailableTo(),
                vehicle.getApprovalStatus(),
                vehicle.getApprovalNotes(),
                vehicle.getReviewedBy(),
                vehicle.getReviewedAt(),
                booked,
                vehicle.getYear(),
                vehicle.getInsurer(),
                vehicle.getPolicyNumber());
    }
}
