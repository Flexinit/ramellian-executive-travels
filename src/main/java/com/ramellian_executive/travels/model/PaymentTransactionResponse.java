package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.PaymentTransaction;

import java.time.ZonedDateTime;
import java.util.UUID;

public record PaymentTransactionResponse(
        UUID id,
        String type,
        String status,
        Double amount,
        String currency,
        String providerReference,
        String internalReference,
        String phone,
        String partyName,
        String email,
        String renterPhone,
        String renterName,
        String description,
        UUID bookingId,
        UUID paymentId,
        UUID disbursementId,
        UUID splitContributionId,
        String providerMessage,
        String rawCallback,
        // M-Pesa STK Push callback fields
        String mpesaMerchantRequestId,
        String mpesaCheckoutRequestId,
        Integer mpesaResultCode,
        String mpesaResultDesc,
        String mpesaReceiptNumber,
        String mpesaTransactionDate,
        String mpesaPhoneNumber,
        String mpesaBalance,
        ZonedDateTime createdAt,
        ZonedDateTime lastModifiedAt
) {
    public static PaymentTransactionResponse from(PaymentTransaction t) {
        return new PaymentTransactionResponse(
                t.getId(),
                t.getType() != null ? t.getType().name() : null,
                t.getStatus(),
                t.getAmount(),
                t.getCurrency(),
                t.getProviderReference(),
                t.getInternalReference(),
                t.getPhone(),
                t.getPartyName(),
                t.getEmail(),
                t.getRenterPhone(),
                t.getRenterName(),
                t.getDescription(),
                t.getBookingId(),
                t.getPaymentId(),
                t.getDisbursementId(),
                t.getSplitContributionId(),
                t.getProviderMessage(),
                t.getRawCallback(),
                t.getMpesaMerchantRequestId(),
                t.getMpesaCheckoutRequestId(),
                t.getMpesaResultCode(),
                t.getMpesaResultDesc(),
                t.getMpesaReceiptNumber(),
                t.getMpesaTransactionDate(),
                t.getMpesaPhoneNumber(),
                t.getMpesaBalance(),
                t.getCreatedAt(),
                t.getLastModifiedAt()
        );
    }
}
