package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.TripType;
import com.ramellian_executive.travels.entity.Vehicle;

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
        String ownerEmail) {

    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getModel(), vehicle.getVIN(),
                vehicle.getRegistrationNumber(), vehicle.getLocation(), vehicle.getPricePerDay(),
                vehicle.getRating(), vehicle.isAvailable(), vehicle.getCapacity(), vehicle.getColor(),
                vehicle.getFuelType(), vehicle.getTransmissionType(), vehicle.isSelfDrive(),
                vehicle.isChauffeured(), vehicle.getTripTypes(), vehicle.getOwner().getEmailAddress());
    }
}
