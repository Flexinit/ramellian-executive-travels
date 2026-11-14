package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.StkCallbackResult;

import java.time.ZonedDateTime;
import java.util.UUID;

public record StkCallbackResultResponse(
        UUID id,
        String merchantRequestId,
        String checkoutRequestId,
        Integer resultCode,
        String resultDesc,
        String mpesaReceiptNumber,
        Double amount,
        String transactionDate,
        String phoneNumber,
        String balance,
        UUID paymentId,
        UUID bookingId,
        ZonedDateTime receivedAt
) {
    public static StkCallbackResultResponse from(StkCallbackResult e) {
        return new StkCallbackResultResponse(
                e.getId(),
                e.getMerchantRequestId(),
                e.getCheckoutRequestId(),
                e.getResultCode(),
                e.getResultDesc(),
                e.getMpesaReceiptNumber(),
                e.getAmount(),
                e.getTransactionDate(),
                e.getPhoneNumber(),
                e.getBalance(),
                e.getPaymentId(),
                e.getBookingId(),
                e.getReceivedAt()
        );
    }
}
