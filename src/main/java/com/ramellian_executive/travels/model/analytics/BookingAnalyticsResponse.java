package com.ramellian_executive.travels.model.analytics;

import java.util.Map;

public record BookingAnalyticsResponse(
    long totalBookings,
    Map<String, Long> countByStatus,
    double cancellationRate,
    double confirmationRate,
    long completedCount,
    long cancelledCount
) {}
