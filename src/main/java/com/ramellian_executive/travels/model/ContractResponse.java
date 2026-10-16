package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.BookingContract;
import com.ramellian_executive.travels.entity.ContractStatus;

import java.time.ZonedDateTime;
import java.util.UUID;

public record ContractResponse(
        UUID id,
        UUID bookingId,
        String status,
        String contractText,
        ZonedDateTime renterSignedAt,
        String renterSignedBy,
        ZonedDateTime ownerSignedAt,
        String ownerSignedBy,
        String documentHash,
        boolean signedDocumentAvailable,
        boolean fullySignedDocumentAvailable,
        ZonedDateTime reviewedAt,
        ZonedDateTime acceptedAt,
        boolean paymentRequired
) {
    public static ContractResponse from(BookingContract contract) {
        boolean fullySignedAvailable = contract.getStatus() == ContractStatus.FULLY_SIGNED
                && contract.getSignedFileName() != null;
        return new ContractResponse(
                contract.getId(),
                contract.getBooking().getId(),
                contract.getStatus().name(),
                contract.getContractText(),
                contract.getSignedAt(),
                contract.getSignedBy(),
                contract.getOwnerSignedAt(),
                contract.getOwnerSignedBy(),
                contract.getDocumentHash(),
                contract.getSignedFileName() != null,
                fullySignedAvailable,
                contract.getReviewedAt(),
                contract.getAcceptedAt(),
                contract.getAcceptedAt() != null && contract.getSignedAt() == null
        );
    }
}
