package com.ramellian_executive.travels.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchBookingRequest(
    @NotEmpty @Valid List<BookingRequest> bookings
) {}
