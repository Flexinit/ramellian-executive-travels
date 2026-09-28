package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.UserDocument;

import java.time.ZonedDateTime;
import java.util.UUID;

public record DocumentResponse(UUID id, UUID userId, String userEmail, String userName,
                               String documentType, UUID vehicleId,
                               String originalFileName, String contentType,
                               long fileSize, String reviewStatus, String reviewNotes,
                               ZonedDateTime createdAt) {
    public static DocumentResponse from(UserDocument document) {
        String userName = null;
        if (document.getUser() != null) {
            String first = document.getUser().getFirstName();
            String last = document.getUser().getLastName();
            userName = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
        }
        return new DocumentResponse(
                document.getId(),
                document.getUser() == null ? null : document.getUser().getId(),
                document.getUser() == null ? null : document.getUser().getEmailAddress(),
                userName,
                document.getDocumentType().name(),
                document.getVehicle() == null ? null : document.getVehicle().getId(),
                document.getOriginalFileName(), document.getContentType(),
                document.getFileSize(), document.getReviewStatus().name(),
                document.getReviewNotes(), document.getCreatedAt());
    }
}
