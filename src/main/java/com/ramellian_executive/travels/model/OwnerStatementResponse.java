package com.ramellian_executive.travels.model;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record OwnerStatementResponse(
        ZonedDateTime generatedAt,
        ZonedDateTime from,
        ZonedDateTime to,
        Summary summary,
        List<MonthlyBreakdown> monthlyBreakdown,
        List<VehicleBreakdown> vehicleBreakdown,
        List<StatementEntry> entries
) {
    public record Summary(
            int totalBookings,
            double totalGrossRevenue,
            double totalPlatformFees,
            double totalTransactionFees,
            double totalPayout
    ) {}

    public record MonthlyBreakdown(
            int year,
            int month,
            String label,
            int bookingCount,
            double grossRevenue,
            double payout
    ) {}

    public record VehicleBreakdown(
            UUID vehicleId,
            String vehicleTitle,
            int bookingCount,
            double grossRevenue,
            double payout
    ) {}

    public record StatementEntry(
            UUID bookingId,
            UUID paymentId,
            String vehicleTitle,
            String renterName,
            String renterEmail,
            ZonedDateTime startTime,
            ZonedDateTime endTime,
            long numberOfDays,
            double baseAmount,
            double platformFee,
            double transactionFee,
            double ownerPayout,
            String paymentMethod,
            ZonedDateTime paidAt,
            String receiptNumber
    ) {}
}
