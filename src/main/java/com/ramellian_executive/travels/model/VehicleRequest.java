package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.TripType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import java.util.Set;

public record VehicleRequest(
        @NotBlank String model,
        @NotBlank String vin,
        @NotBlank String registrationNumber,
        @NotBlank String location,
        @NotNull @DecimalMin("0.0") Double pricePerDay,
        @Min(1) int capacity,
        @NotBlank String color,
        @NotBlank String fuelType,
        @NotBlank String transmissionType,
        boolean selfDrive,
        boolean chauffeured,
        Set<TripType> tripTypes,
        boolean available,
        ZonedDateTime availableFrom,
        ZonedDateTime availableTo,
        boolean hasWorkingAC,
        boolean comprehensiveInsurance,
        String insuranceVerificationStickerPhoto,
        Integer year,
        String insurer,
        String policyNumber
) {
    public VehicleRequest {
        tripTypes = (tripTypes == null) ? Set.of() : Set.copyOf(tripTypes);
    }
}