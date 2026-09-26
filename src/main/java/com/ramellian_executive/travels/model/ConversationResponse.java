package com.ramellian_executive.travels.model;

import java.time.ZonedDateTime;
import java.util.UUID;

public record ConversationResponse(
        UUID otherUserId,
        String otherUserName,
        String otherUserEmail,
        UUID bookingId,
        UUID vehicleId,
        String vehicleModel,
        String lastMessage,
        ZonedDateTime lastMessageTime,
        long unreadCount
) {
}
