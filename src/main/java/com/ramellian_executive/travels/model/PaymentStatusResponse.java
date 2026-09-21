package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.Payment;

import java.util.UUID;

public record PaymentStatusResponse(UUID paymentId, UUID bookingId, String provider,
                                    String status, Double amount, Double platformFee,
                                    Double ownerPayout, String orderTrackingId,
                                    String receipt) {
    public static PaymentStatusResponse from(Payment payment) {
        return new PaymentStatusResponse(payment.getId(), payment.getBooking().getId(),
                payment.getProvider(), payment.getStatus().name(), payment.getAmount(),
                payment.getPlatformFee(), payment.getOwnerPayout(),
                payment.getProviderOrderTrackingId(), payment.getProviderReceipt());
    }
}
