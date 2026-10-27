package com.ramellian_executive.travels.model.analytics;

import java.time.LocalDate;

public record PlatformSummaryResponse(
    // Revenue
    double grossRevenue,
    double platformEarnings,        // platform's commission = actual profit
    double ownerPayoutsEarned,      // what owners are owed (sum from payments)
    double transactionFees,
    double deliveryFees,
    double driverCharges,
    double avgBookingValue,

    // Payments
    long successfulPayments,
    long pendingPayments,
    long failedPayments,

    // Disbursements (all-time totals)
    double totalDisbursed,
    double pendingDisbursements,

    // Bookings in period
    long totalBookings,
    long pendingBookings,
    long confirmedBookings,
    long activeBookings,
    long completedBookings,
    long cancelledBookings,
    long rejectedBookings,

    // Growth vs prior equal-length period (null = no prior data)
    Double revenueGrowthPct,
    Double earningsGrowthPct,
    Double bookingGrowthPct,

    LocalDate from,
    LocalDate to
) {}
