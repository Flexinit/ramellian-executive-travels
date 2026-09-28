package com.ramellian_executive.travels.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BookingGroupResponse(
    UUID groupId,
    String status,
    List<BookingResponse> bookings,
    BigDecimal totalEstimatedAmount
) {}
