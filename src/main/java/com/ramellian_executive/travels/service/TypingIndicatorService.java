package com.ramellian_executive.travels.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TypingIndicatorService {

    // recipientUserId -> their active SSE emitter
    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5-minute timeout
        emitters.put(userId, emitter);
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError(e -> emitters.remove(userId));
        return emitter;
    }

    public void sendTyping(UUID senderId, UUID recipientId, UUID bookingId, boolean typing) {
        SseEmitter emitter = emitters.get(recipientId);
        if (emitter == null) return;
        try {
            String data = buildPayload(senderId, bookingId, typing);
            emitter.send(SseEmitter.event().name("typing").data(data));
        } catch (IOException e) {
            emitters.remove(recipientId);
        }
    }

    private String buildPayload(UUID senderId, UUID bookingId, boolean typing) {
        String bookingPart = bookingId != null ? ",\"bookingId\":\"" + bookingId + "\"" : "";
        return "{\"senderId\":\"" + senderId + "\"" + bookingPart + ",\"typing\":" + typing + "}";
    }
}
