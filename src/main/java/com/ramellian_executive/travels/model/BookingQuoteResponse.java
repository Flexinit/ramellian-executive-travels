package com.ramellian_executive.travels.model;

import java.math.BigDecimal;
import java.util.UUID;

public record BookingQuoteResponse(
    UUID vehicleId,
    long numberOfDays,
    BigDecimal pricePerDay,
    BigDecimal estimatedAmount
) {}
