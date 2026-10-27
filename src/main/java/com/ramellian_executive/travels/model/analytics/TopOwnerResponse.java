package com.ramellian_executive.travels.model.analytics;

public record TopOwnerResponse(
    String ownerId,
    String name,
    String email,
    long vehicleCount,
    long bookingCount,
    double grossRevenue,
    double platformEarnings,
    double ownerPayouts
) {}
