package com.ramellian_executive.travels.entity;

public enum SplitPaymentGroupStatus {
    PENDING,    // created, not all contributions paid
    PARTIAL,    // some contributions paid, others pending
    COMPLETE,   // all contributions paid — master payment created
    EXPIRED,    // payment window elapsed before completion
    CANCELLED
}
