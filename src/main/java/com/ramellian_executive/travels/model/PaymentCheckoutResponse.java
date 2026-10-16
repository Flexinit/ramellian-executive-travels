package com.ramellian_executive.travels.model;

import java.util.UUID;

public record PaymentCheckoutResponse(UUID paymentId, String provider,
                                      String orderTrackingId, String redirectUrl,
                                      String status,
                                      Double baseAmount,
                                      Double transactionFee,
                                      Double deliveryFee,
                                      Double driverCharge,
                                      Double platformFee,
                                      Double totalAmount) {

    public PaymentCheckoutResponse(UUID paymentId, String provider,
                                   String orderTrackingId, String redirectUrl,
                                   String status) {
        this(paymentId, provider, orderTrackingId, redirectUrl, status,
                null, null, null, null, null, null);
    }
}
