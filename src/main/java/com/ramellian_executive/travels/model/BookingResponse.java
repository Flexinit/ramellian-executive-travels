package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.Booking;
import com.ramellian_executive.travels.entity.TripType;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record BookingResponse(UUID id, UUID vehicleId,
                              String renterEmail, String renterName, String renterPhone,
                              ZonedDateTime startTime, ZonedDateTime endTime,
                              String status, String bookingMode, Double baseAmount,
                              Double platformFee, Double estimatedAmount,
                              Double ownerPayout,
                              Long numberOfDays, TripType tripType, String renterNotes,
                              String dispatcherNotes, String chauffeurEmail,
                              ZonedDateTime actualEndTime, Long overstayDays, Double overstayAmount,
                              String pickupLocation, String dropoffLocation,
                              String renterContactEmail, String renterContactPhone,
                              boolean deliveryRequested, Double deliveryFee, Double driverCharge,
                              Integer passengerCount,
                              List<String> warnings) {

    public static BookingResponse from(Booking booking) {
        return from(booking, 1.0, 0.0, 0.0, 0.0);
    }

    public BookingResponse withWarnings(List<String> warnings) {
        return new BookingResponse(id, vehicleId, renterEmail, renterName, renterPhone,
                startTime, endTime, status, bookingMode, baseAmount, platformFee, estimatedAmount,
                ownerPayout, numberOfDays, tripType, renterNotes, dispatcherNotes, chauffeurEmail,
                actualEndTime, overstayDays, overstayAmount,
                pickupLocation, dropoffLocation, renterContactEmail, renterContactPhone,
                deliveryRequested, deliveryFee, driverCharge, passengerCount, warnings);
    }

    public BookingResponse withActualPaymentValues(Double paidAmount, Double paidPlatformFee, Double paidOwnerPayout) {
        return new BookingResponse(id, vehicleId, renterEmail, renterName, renterPhone,
                startTime, endTime, status,
                bookingMode,
                paidAmount != null ? paidAmount - (paidPlatformFee != null ? paidPlatformFee : 0.0) : baseAmount,
                paidPlatformFee != null ? paidPlatformFee : platformFee,
                paidAmount != null ? paidAmount : estimatedAmount,
                paidOwnerPayout != null ? paidOwnerPayout : ownerPayout,
                numberOfDays, tripType, renterNotes, dispatcherNotes, chauffeurEmail,
                actualEndTime, overstayDays, overstayAmount,
                pickupLocation, dropoffLocation, renterContactEmail, renterContactPhone,
                deliveryRequested, deliveryFee, driverCharge, passengerCount, warnings);
    }

    public BookingResponse withoutOwnerPayout() {
        return new BookingResponse(id, vehicleId, renterEmail, renterName, renterPhone,
                startTime, endTime, status,
                bookingMode, baseAmount, platformFee, estimatedAmount, null,
                numberOfDays, tripType, renterNotes, dispatcherNotes, chauffeurEmail,
                actualEndTime, overstayDays, overstayAmount,
                pickupLocation, dropoffLocation, renterContactEmail, renterContactPhone,
                deliveryRequested, deliveryFee, driverCharge, passengerCount, warnings);
    }

    public static BookingResponse from(Booking booking, double commissionRatePct) {
        return from(booking, commissionRatePct, 0.0, 0.0, 0.0);
    }

    public static BookingResponse from(Booking booking, double commissionRatePct, double txFeeRatePct) {
        return from(booking, commissionRatePct, txFeeRatePct, 0.0, 0.0);
    }

    public static BookingResponse from(Booking booking, double commissionRatePct, double txFeeRatePct,
                                       double deliveryFlatFee) {
        return from(booking, commissionRatePct, txFeeRatePct, deliveryFlatFee, 0.0);
    }

    public static BookingResponse from(Booking booking, double commissionRatePct, double txFeeRatePct,
                                       double deliveryFlatFee, double driverFlatCharge) {
        long totalHours = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
        long days = Math.max(1, (totalHours + 23) / 24);
        double base = booking.getVehicle().getPricePerDay() * days;
        double fee = base * (commissionRatePct / 100.0);
        double txFee = base * (txFeeRatePct / 100.0);
        double ownerPayout = base - fee;
        double deliveryFee = booking.isDeliveryRequested() ? deliveryFlatFee : 0.0;
        double driverCharge = booking.getBookingMode() == com.ramellian_executive.travels.entity.BookingMode.CHAUFFEURED
                ? driverFlatCharge : 0.0;
        Long overstayDays = null;
        Double overstayAmount = null;
        if (booking.getActualEndTime() != null && booking.getActualEndTime().isAfter(booking.getEndTime())) {
            long overstayHours = java.time.Duration.between(booking.getEndTime(), booking.getActualEndTime()).toHours();
            overstayDays = Math.max(1, (overstayHours + 23) / 24);
            overstayAmount = booking.getVehicle().getPricePerDay() * overstayDays;
        }
        com.ramellian_executive.travels.entity.User renter = booking.getUser();
        String renterName = java.util.stream.Stream.of(renter.getFirstName(), renter.getLastName())
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
        return new BookingResponse(booking.getId(), booking.getVehicle().getId(),
                renter.getEmailAddress(),
                renterName.isBlank() ? null : renterName,
                renter.getPhoneNumber(),
                booking.getStartTime(), booking.getEndTime(), booking.getStatus().name(),
                booking.getBookingMode().name(),
                base, fee, base + txFee + deliveryFee + driverCharge, ownerPayout,
                days,
                booking.getTripType(),
                booking.getRenterNotes(),
                booking.getDispatcherNotes(),
                booking.getChauffeur() == null ? null : booking.getChauffeur().getEmailAddress(),
                booking.getActualEndTime(), overstayDays, overstayAmount,
                booking.getPickupLocation(), booking.getDropoffLocation(),
                booking.getRenterContactEmail(), booking.getRenterContactPhone(),
                booking.isDeliveryRequested(), deliveryFee, driverCharge,
                booking.getPassengerCount(),
                null);
    }
}
