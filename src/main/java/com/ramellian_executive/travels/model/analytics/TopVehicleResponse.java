package com.ramellian_executive.travels.model.analytics;

public record TopVehicleResponse(
    String vehicleId,
    String model,
    String registrationNumber,
    long bookingCount,
    double grossRevenue,
    double platformEarnings
) {}
