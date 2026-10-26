package com.ramellian_executive.travels.model;

public record ManualDisbursementRequest(
        Double amount,
        String ownerPhone,
        String notes
) {}
