package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.Booking;
import com.ramellian_executive.travels.entity.TripType;
import java.time.ZonedDateTime;
import java.util.UUID;

public record BookingResponse(UUID id, UUID vehicleId, String renterEmail,
                              ZonedDateTime startTime, ZonedDateTime endTime,
                              String status, String bookingMode, Double estimatedAmount,
                              Long numberOfDays, TripType tripType, String renterNotes,
                              String dispatcherNotes, String chauffeurEmail,
                              ZonedDateTime actualEndTime, Long overstayDays, Double overstayAmount) {
    public static BookingResponse from(Booking booking) {
        long totalHours = java.time.Duration.between(booking.getStartTime(), booking.getEndTime()).toHours();
        long days = Math.max(1, (totalHours + 23) / 24);
        Long overstayDays = null;
        Double overstayAmount = null;
        if (booking.getActualEndTime() != null && booking.getActualEndTime().isAfter(booking.getEndTime())) {
            long overstayHours = java.time.Duration.between(booking.getEndTime(), booking.getActualEndTime()).toHours();
            overstayDays = Math.max(1, (overstayHours + 23) / 24);
            overstayAmount = booking.getVehicle().getPricePerDay() * overstayDays;
        }
        return new BookingResponse(booking.getId(), booking.getVehicle().getId(),
                booking.getUser().getEmailAddress(), booking.getStartTime(),
                booking.getEndTime(), booking.getStatus().name(),
                booking.getBookingMode().name(),
                booking.getVehicle().getPricePerDay() * days,
                days,
                booking.getTripType(),
                booking.getRenterNotes(),
                booking.getDispatcherNotes(),
                booking.getChauffeur() == null ? null : booking.getChauffeur().getEmailAddress(),
                booking.getActualEndTime(), overstayDays, overstayAmount);
    }
}
