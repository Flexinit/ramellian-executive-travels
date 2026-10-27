package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.Refund;

import java.time.ZonedDateTime;
import java.util.UUID;

public record RefundResponse(
        UUID id,
        UUID paymentId,
        UUID bookingId,
        String vehicleTitle,
        Double amount,
        String reason,
        String status,
        String pesapalRefundTrackingId,
        String initiatedBy,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static RefundResponse from(Refund r) {
        String vehicleTitle = null;
        UUID bookingId = null;
        if (r.getPayment() != null && r.getPayment().getBooking() != null) {
            bookingId = r.getPayment().getBooking().getId();
            var vehicle = r.getPayment().getBooking().getVehicle();
            if (vehicle != null) {
                vehicleTitle = (vehicle.getModel() != null ? vehicle.getModel() : "Vehicle")
                        + (vehicle.getRegistrationNumber() != null && !vehicle.getRegistrationNumber().isBlank()
                        ? " (" + vehicle.getRegistrationNumber() + ")" : "");
            }
        }
        return new RefundResponse(
                r.getId(),
                r.getPayment() != null ? r.getPayment().getId() : null,
                bookingId,
                vehicleTitle,
                r.getAmount(),
                r.getReason(),
                r.getStatus() != null ? r.getStatus().name() : null,
                r.getPesapalRefundTrackingId(),
                r.getInitiatedBy(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
