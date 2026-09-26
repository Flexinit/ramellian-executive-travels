package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.AppNotification;

import java.time.ZonedDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID recipientId,
        String title,
        String message,
        String type,
        UUID referenceId,
        String referenceType,
        boolean read,
        ZonedDateTime readAt,
        ZonedDateTime createdAt
) {
    public static NotificationResponse from(AppNotification notification) {
        if (notification == null) {
            return null;
        }
        return new NotificationResponse(
                notification.getId(),
                notification.getRecipient() != null ? notification.getRecipient().getId() : null,
                notification.getTitle(),
                notification.getMessage(),
                notification.getType(),
                notification.getReferenceId(),
                notification.getReferenceType(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
