package com.ramellian_executive.travels.model;

import java.time.ZonedDateTime;
import java.util.List;

public record BookingReportResponse(
        ZonedDateTime generatedAt,
        int totalBookings,
        int upcomingBookings,
        int activeBookings,
        int completedBookings,
        int cancelledBookings,
        int rejectedBookings,
        double successfulGrossAmount,
        double platformRevenue,
        double ownerPayout,
        List<BookingResponse> upcoming,
        List<BookingResponse> active,
        List<BookingResponse> completed,
        List<BookingResponse> cancelled,
        List<BookingResponse> rejected) {
}
