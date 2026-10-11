package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.BookingContract;

import java.time.ZonedDateTime;
import java.util.UUID;

public record ContractResponse(UUID id, UUID bookingId, String status,
                               String contractText, ZonedDateTime signedAt,
                               String documentHash, boolean signedDocumentAvailable,
                               ZonedDateTime reviewedAt, ZonedDateTime acceptedAt,
                               boolean paymentRequired) {
    public static ContractResponse from(BookingContract contract) {
        return new ContractResponse(contract.getId(), contract.getBooking().getId(),
                contract.getStatus().name(), contract.getContractText(),
                contract.getSignedAt(), contract.getDocumentHash(),
                contract.getSignedFileName() != null, contract.getReviewedAt(),
                contract.getAcceptedAt(), contract.getAcceptedAt() != null && contract.getSignedAt() == null);
    }
}
