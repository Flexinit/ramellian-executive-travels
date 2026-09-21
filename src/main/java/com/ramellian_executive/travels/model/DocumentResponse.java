package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.UserDocument;

import java.time.ZonedDateTime;
import java.util.UUID;

public record DocumentResponse(UUID id, String documentType, UUID vehicleId,
                               String originalFileName, String contentType,
                               long fileSize, String reviewStatus, String reviewNotes,
                               ZonedDateTime createdAt) {
    public static DocumentResponse from(UserDocument document) {
        return new DocumentResponse(        document.getId(), document.getDocumentType().name(),
                document.getVehicle() == null ? null : document.getVehicle().getId(),
                document.getOriginalFileName(), document.getContentType(),
                document.getFileSize(), document.getReviewStatus().name(),
                document.getReviewNotes(), document.getCreatedAt());
    }
}
