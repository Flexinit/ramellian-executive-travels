package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BookingFinancialSummary(

        // ── Booking ──────────────────────────────────────────────────────
        UUID          bookingId,
        String        bookingStatus,
        String        vehicleTitle,
        String        renterEmail,
        String        ownerEmail,
        ZonedDateTime startTime,
        ZonedDateTime endTime,
        ZonedDateTime actualEndTime,
        long          agreedDays,
        long          overstayHours,
        long          overstayDaysCharged,

        // ── Per-charge breakdown (RENTAL, OVERSTAY, …) ──────────────────
        List<ChargeRecord> charges,

        // ── Aggregated totals ────────────────────────────────────────────
        double totalBilledToRenter,
        double totalCollectedFromRenter,
        double totalPlatformCommission,
        double totalProcessingFees,
        double totalOwnerPayoutsPromised,
        double totalOwnerPayoutsDisbursed,
        double totalOwnerPayoutsOutstanding,
        double platformNetProfit,

        // ── Settlement flags ─────────────────────────────────────────────
        boolean allChargesPaid,
        boolean allDisbursementsComplete,
        boolean fullySettled

) {

    public record ChargeRecord(
            UUID          paymentId,
            String        chargeType,
            double        baseAmount,
            double        platformCommission,
            double        processingFee,
            double        deliveryFee,
            double        driverCharge,
            double        totalCharged,
            double        ownerPayoutPromised,
            String        paymentStatus,
            String        paymentMethod,
            String        paymentReceipt,
            ZonedDateTime paymentDate,

            // Disbursement for this charge
            UUID          disbursementId,
            double        ownerPayoutDisbursed,
            double        ownerPayoutShortfall,
            String        disbursementStatus,
            String        disbursementReference,
            ZonedDateTime disbursementDate,

            boolean       charged,    // payment SUCCESSFUL or REFUNDED
            boolean       disbursed   // disbursement COMPLETED
    ) {}

    public static BookingFinancialSummary from(Booking booking,
                                               List<Payment> payments,
                                               Map<UUID, Disbursement> disbursementByPaymentId) {
        var vehicle = booking.getVehicle();
        var renter  = booking.getUser();
        var owner   = vehicle.getOwner();

        String vehicleTitle = (vehicle.getModel() != null ? vehicle.getModel() : "Vehicle")
                + (vehicle.getRegistrationNumber() != null && !vehicle.getRegistrationNumber().isBlank()
                ? " (" + vehicle.getRegistrationNumber() + ")" : "");

        long agreedDays = daysBetween(booking.getStartTime(), booking.getEndTime());
        long overstayHours = 0;
        long overstayDaysCharged = 0;
        if (booking.getActualEndTime() != null && booking.getActualEndTime().isAfter(booking.getEndTime())) {
            overstayHours = java.time.Duration.between(booking.getEndTime(), booking.getActualEndTime()).toHours();
            // find overstay payment to get how many days were charged
            overstayDaysCharged = payments.stream()
                    .filter(p -> p.getChargeType() == PaymentChargeType.OVERSTAY)
                    .mapToLong(p -> {
                        double base = safe(p.getAmount()) - safe(p.getTransactionFee())
                                - safe(p.getDeliveryFee()) - safe(p.getDriverCharge());
                        double dailyRate = vehicle.getPricePerDay() != null ? vehicle.getPricePerDay() : 1;
                        return Math.round(base / dailyRate);
                    })
                    .sum();
        }

        List<ChargeRecord> charges = payments.stream().map(p -> {
            Disbursement d = disbursementByPaymentId.get(p.getId());

            double base       = safe(p.getAmount()) - safe(p.getTransactionFee())
                                - safe(p.getDeliveryFee()) - safe(p.getDriverCharge());
            double commission = safe(p.getPlatformFee());
            double processing = safe(p.getTransactionFee());
            double delivery   = safe(p.getDeliveryFee());
            double driver     = safe(p.getDriverCharge());
            double total      = safe(p.getAmount());
            double promised   = safe(p.getOwnerPayout());

            double disbursed  = d != null ? safe(d.getAmount()) : 0.0;
            double shortfall  = promised - disbursed;
            boolean charged   = p.getStatus() == PaymentStatus.SUCCESSFUL
                                || p.getStatus() == PaymentStatus.REFUNDED;
            boolean paid      = d != null && d.getStatus() == DisbursementStatus.COMPLETED;

            return new ChargeRecord(
                    p.getId(),
                    p.getChargeType() != null ? p.getChargeType().name() : "UNKNOWN",
                    base, commission, processing, delivery, driver, total, promised,
                    p.getStatus().name(), p.getPaymentMethod(), p.getProviderReceipt(),
                    p.getUpdatedAt(),
                    d != null ? d.getId() : null,
                    disbursed, shortfall,
                    d != null && d.getStatus() != null ? d.getStatus().name() : null,
                    d != null ? d.getReference() : null,
                    d != null ? d.getTriggeredAt() : null,
                    charged, paid
            );
        }).toList();

        double totalBilled      = charges.stream().mapToDouble(ChargeRecord::totalCharged).sum();
        double totalCollected   = charges.stream().filter(ChargeRecord::charged)
                                         .mapToDouble(ChargeRecord::totalCharged).sum();
        double totalCommission  = charges.stream().mapToDouble(ChargeRecord::platformCommission).sum();
        double totalProcessing  = charges.stream().mapToDouble(ChargeRecord::processingFee).sum();
        double totalPromised    = charges.stream().mapToDouble(ChargeRecord::ownerPayoutPromised).sum();
        double totalDisbursed   = charges.stream().mapToDouble(ChargeRecord::ownerPayoutDisbursed).sum();
        double totalOutstanding = charges.stream()
                                         .filter(c -> !c.disbursed() && c.charged())
                                         .mapToDouble(ChargeRecord::ownerPayoutPromised).sum();

        boolean allPaid     = charges.stream().allMatch(ChargeRecord::charged);
        boolean allDisbursed = charges.stream().filter(ChargeRecord::charged)
                                      .allMatch(ChargeRecord::disbursed);

        return new BookingFinancialSummary(
                booking.getId(), booking.getStatus().name(), vehicleTitle,
                renter.getEmailAddress(),
                owner != null ? owner.getEmailAddress() : null,
                booking.getStartTime(), booking.getEndTime(), booking.getActualEndTime(),
                agreedDays, overstayHours, overstayDaysCharged,
                charges,
                totalBilled, totalCollected, totalCommission, totalProcessing,
                totalPromised, totalDisbursed, totalOutstanding,
                totalCommission,   // net profit = commission (processing fully covered by renter)
                allPaid, allDisbursed, allPaid && allDisbursed
        );
    }

    private static long daysBetween(ZonedDateTime from, ZonedDateTime to) {
        long hours = java.time.Duration.between(from, to).toHours();
        return Math.max(1, (hours + 23) / 24);
    }

    private static double safe(Double v) { return v == null ? 0.0 : v; }
}
