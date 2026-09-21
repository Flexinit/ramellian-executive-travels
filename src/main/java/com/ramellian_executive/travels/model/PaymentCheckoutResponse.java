package com.ramellian_executive.travels.model;

import java.util.UUID;

public record PaymentCheckoutResponse(UUID paymentId, String provider,
                                      String orderTrackingId, String redirectUrl,
                                      String status) {
}
