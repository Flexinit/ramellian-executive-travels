package com.ramellian_executive.travels.entity;

public enum PaymentTransactionType {
    /** M-Pesa STK Push — customer pays the platform (C2B). */
    MPESA_STK,
    /** M-Pesa STK Push contribution inside a split-payment group. */
    MPESA_SPLIT_STK,
    /** M-Pesa B2C disbursement — platform pays out to vehicle owner. */
    MPESA_B2C,
    /** PesaPal card / online payment — customer pays the platform (C2B). */
    PESAPAL_CARD,
    /** PesaPal disbursement — platform pays out to vehicle owner. */
    PESAPAL_B2C,
    /** M-Pesa C2B — customer pays via Paybill/Till (no STK prompt). */
    MPESA_C2B
}
