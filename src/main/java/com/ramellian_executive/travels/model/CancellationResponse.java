package com.ramellian_executive.travels.model;

import java.util.UUID;

public record CancellationResponse(
        UUID bookingId,
        String bookingStatus,
        double paidAmount,
        double refundAmount,
        int refundPercent,
        String message
) {}
