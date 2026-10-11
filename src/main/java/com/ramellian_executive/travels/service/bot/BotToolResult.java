package com.ramellian_executive.travels.service.bot;

import java.util.List;
import java.util.UUID;

public record BotToolResult(
        String text,
        List<BotMedia> media,
        UUID bookingId,
        UUID contractId
) {
    public static BotToolResult text(String text) {
        return new BotToolResult(text, List.of(), null, null);
    }

    public static BotToolResult withMedia(String text, List<BotMedia> media) {
        return new BotToolResult(text, media, null, null);
    }

    public static BotToolResult withBooking(String text, UUID bookingId) {
        return new BotToolResult(text, List.of(), bookingId, null);
    }

    public static BotToolResult withContract(String text, UUID contractId) {
        return new BotToolResult(text, List.of(), null, contractId);
    }
}
