package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.BookingLead;

import java.time.ZonedDateTime;
import java.util.UUID;

public record BookingLeadResponse(
    UUID id,
    String email,
    String phoneNumber,
    UUID vehicleId,
    ZonedDateTime startTime,
    ZonedDateTime endTime,
    String notes,
    ZonedDateTime createdAt
) {
    public static BookingLeadResponse from(BookingLead lead) {
        return new BookingLeadResponse(
            lead.getId(),
            lead.getEmail(),
            lead.getPhoneNumber(),
            lead.getVehicleId(),
            lead.getStartTime(),
            lead.getEndTime(),
            lead.getNotes(),
            lead.getLeadCreatedAt()
        );
    }
}
