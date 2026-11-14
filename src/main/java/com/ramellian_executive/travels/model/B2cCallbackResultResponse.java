package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.B2cCallbackResult;

import java.time.ZonedDateTime;
import java.util.UUID;

public record B2cCallbackResultResponse(
        UUID id,
        UUID disbursementId,
        Integer resultType,
        Integer resultCode,
        String resultDesc,
        String originatorConversationId,
        String conversationId,
        String transactionId,
        Double transactionAmount,
        String transactionReceipt,
        String receiverPartyPublicName,
        String transactionCompletedDateTime,
        Double b2cUtilityAccountAvailableFunds,
        Double b2cWorkingAccountAvailableFunds,
        String recipientIsRegisteredCustomer,
        Double b2cChargesPaidAccountAvailableFunds,
        ZonedDateTime receivedAt
) {
    public static B2cCallbackResultResponse from(B2cCallbackResult e) {
        return new B2cCallbackResultResponse(
                e.getId(),
                e.getDisbursement() != null ? e.getDisbursement().getId() : null,
                e.getResultType(),
                e.getResultCode(),
                e.getResultDesc(),
                e.getOriginatorConversationId(),
                e.getConversationId(),
                e.getTransactionId(),
                e.getTransactionAmount(),
                e.getTransactionReceipt(),
                e.getReceiverPartyPublicName(),
                e.getTransactionCompletedDateTime(),
                e.getB2cUtilityAccountAvailableFunds(),
                e.getB2cWorkingAccountAvailableFunds(),
                e.getRecipientIsRegisteredCustomer(),
                e.getB2cChargesPaidAccountAvailableFunds(),
                e.getReceivedAt()
        );
    }
}
