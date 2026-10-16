package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.Disbursement;

import java.time.ZonedDateTime;
import java.util.UUID;

public record DisbursementResponse(
        UUID id,
        UUID paymentId,
        UUID bookingId,
        String vehicleTitle,
        String ownerEmail,
        String ownerName,
        String ownerPhone,
        Double amount,
        String status,
        String triggeredBy,
        ZonedDateTime triggeredAt,
        String reference,
        String notes,
        ZonedDateTime createdAt
) {
    public static DisbursementResponse from(Disbursement d) {
        String vehicleTitle = null;
        UUID bookingId = null;
        if (d.getPayment() != null && d.getPayment().getBooking() != null) {
            bookingId = d.getPayment().getBooking().getId();
            var vehicle = d.getPayment().getBooking().getVehicle();
            if (vehicle != null) {
                vehicleTitle = (vehicle.getModel() != null ? vehicle.getModel() : "Vehicle")
                        + (vehicle.getRegistrationNumber() != null && !vehicle.getRegistrationNumber().isBlank()
                        ? " (" + vehicle.getRegistrationNumber() + ")" : "");
            }
        }
        return new DisbursementResponse(
                d.getId(),
                d.getPayment() != null ? d.getPayment().getId() : null,
                bookingId,
                vehicleTitle,
                d.getOwnerEmail(),
                d.getOwnerName(),
                d.getOwnerPhone(),
                d.getAmount(),
                d.getStatus() != null ? d.getStatus().name() : null,
                d.getTriggeredBy(),
                d.getTriggeredAt(),
                d.getReference(),
                d.getNotes(),
                d.getCreatedAt()
        );
    }
}
