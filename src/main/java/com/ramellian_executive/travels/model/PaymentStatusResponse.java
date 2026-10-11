package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.Payment;

import java.util.UUID;

public record PaymentStatusResponse(UUID paymentId, UUID bookingId, String provider,
                                    String status, Double amount, Double baseRentalAmount,
                                    Double deliveryFee, Double driverCharge, Double transactionFee,
                                    Double platformFee, Double ownerPayout,
                                    String orderTrackingId, String merchantReference,
                                    String receipt) {
    public static PaymentStatusResponse from(Payment payment) {
        long totalHours = java.time.Duration.between(
                payment.getBooking().getStartTime(), payment.getBooking().getEndTime()).toHours();
        long days = Math.max(1, (totalHours + 23) / 24);
        double base = payment.getBooking().getVehicle().getPricePerDay() * days;
        return new PaymentStatusResponse(
                payment.getId(),
                payment.getBooking().getId(),
                payment.getProvider(),
                payment.getStatus().name(),
                payment.getAmount(),
                base,
                payment.getDeliveryFee() != null ? payment.getDeliveryFee() : 0.0,
                payment.getDriverCharge() != null ? payment.getDriverCharge() : 0.0,
                payment.getTransactionFee(),
                payment.getPlatformFee(),
                payment.getOwnerPayout(),
                payment.getProviderOrderTrackingId(),
                payment.getProviderMerchantReference(),
                payment.getProviderReceipt());
    }
}
