package com.ramellian_executive.travels.model;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record ChatMessageRequest(
        UUID bookingId,
        UUID recipientId,
        @NotBlank(message = "Message content is required")
        String message
) {
}
