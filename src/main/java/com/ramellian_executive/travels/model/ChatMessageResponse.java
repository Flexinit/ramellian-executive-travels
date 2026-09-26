package com.ramellian_executive.travels.model;

import com.ramellian_executive.travels.entity.ChatMessage;
import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.Vehicle;

import java.time.ZonedDateTime;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        UUID senderId,
        String senderName,
        UUID recipientId,
        String recipientName,
        UUID bookingId,
        UUID vehicleId,
        String vehicleModel,
        String message,
        boolean read,
        ZonedDateTime readAt,
        ZonedDateTime createdAt
) {
    public static ChatMessageResponse from(ChatMessage chatMessage) {
        if (chatMessage == null) {
            return null;
        }

        String senderName = formatUserName(chatMessage.getSender());
        String recipientName = formatUserName(chatMessage.getRecipient());

        Vehicle vehicle = chatMessage.getVehicle();
        if (vehicle == null && chatMessage.getBooking() != null) {
            vehicle = chatMessage.getBooking().getVehicle();
        }

        String vehicleModel = vehicle != null ? vehicle.getModel() : null;
        UUID vehicleId = vehicle != null ? vehicle.getId() : null;
        UUID bookingId = chatMessage.getBooking() != null ? chatMessage.getBooking().getId() : null;

        return new ChatMessageResponse(
                chatMessage.getId(),
                chatMessage.getSender() != null ? chatMessage.getSender().getId() : null,
                senderName,
                chatMessage.getRecipient() != null ? chatMessage.getRecipient().getId() : null,
                recipientName,
                bookingId,
                vehicleId,
                vehicleModel,
                chatMessage.getMessage(),
                chatMessage.isRead(),
                chatMessage.getReadAt(),
                chatMessage.getCreatedAt()
        );
    }

    private static String formatUserName(User user) {
        if (user == null) {
            return "User";
        }
        String fName = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String lName = user.getLastName() != null ? user.getLastName().trim() : "";
        String fullName = (fName + " " + lName).trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }
        if (user.getEmailAddress() != null && !user.getEmailAddress().isBlank()) {
            return user.getEmailAddress().trim();
        }
        return "User";
    }
}
