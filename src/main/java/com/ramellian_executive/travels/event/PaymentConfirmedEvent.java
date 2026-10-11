package com.ramellian_executive.travels.event;

import com.ramellian_executive.travels.entity.Payment;

public record PaymentConfirmedEvent(Payment payment) {}
