package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.C2bCallbackResult;

import java.time.ZonedDateTime;
import java.util.UUID;

public record C2bCallbackResultResponse(
        UUID id,
        String transactionType,
        String transactionId,
        String transTime,
        Double transAmount,
        String businessShortCode,
        String billRefNumber,
        String invoiceNumber,
        String orgAccountBalance,
        String thirdPartyTransId,
        String msisdn,
        String firstName,
        String middleName,
        String lastName,
        UUID paymentId,
        UUID bookingId,
        ZonedDateTime receivedAt
) {
    public static C2bCallbackResultResponse from(C2bCallbackResult e) {
        return new C2bCallbackResultResponse(
                e.getId(),
                e.getTransactionType(),
                e.getTransactionId(),
                e.getTransTime(),
                e.getTransAmount(),
                e.getBusinessShortCode(),
                e.getBillRefNumber(),
                e.getInvoiceNumber(),
                e.getOrgAccountBalance(),
                e.getThirdPartyTransId(),
                e.getMsisdn(),
                e.getFirstName(),
                e.getMiddleName(),
                e.getLastName(),
                e.getPaymentId(),
                e.getBookingId(),
                e.getReceivedAt()
        );
    }
}
