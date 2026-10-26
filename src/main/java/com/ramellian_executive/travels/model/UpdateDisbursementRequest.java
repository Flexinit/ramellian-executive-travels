package com.ramellian_executive.travels.model;

public record UpdateDisbursementRequest(
        String ownerPhone,
        Double amount,
        String notes
) {}
