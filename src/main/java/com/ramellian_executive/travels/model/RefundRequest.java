package com.ramellian_executive.travels.model;

public record RefundRequest(
        Double amount,
        String reason
) {}
