package com.ramellian_executive.travels.model.analytics;

import java.time.LocalDate;

public record DailyEarningResponse(
    LocalDate date,
    double grossRevenue,
    double platformEarnings,
    double ownerPayouts,
    long paymentCount
) {}
