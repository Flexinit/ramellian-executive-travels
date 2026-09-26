package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.ChatMessageRequest;
import com.ramellian_executive.travels.model.ChatMessageResponse;
import com.ramellian_executive.travels.model.ConversationResponse;
import com.ramellian_executive.travels.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Chat", description = "In-app chat between renters and car owners")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/api/v1/chat/messages")
    @Operation(summary = "Send a chat message")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @Valid @RequestBody ChatMessageRequest request,
            Authentication authentication) {
        ChatMessageResponse response = chatService.sendMessage(request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/chat/messages")
    @Operation(summary = "Get chat messages by booking ID or other user ID")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
            @RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) UUID otherUserId,
            Authentication authentication) {
        return ResponseEntity.ok(chatService.getMessages(bookingId, otherUserId, authentication));
    }

    @GetMapping("/api/v1/chat/conversations")
    @Operation(summary = "Get all conversations for the current user")
    public ResponseEntity<List<ConversationResponse>> getConversations(Authentication authentication) {
        return ResponseEntity.ok(chatService.getConversations(authentication));
    }

    @PutMapping("/api/v1/chat/messages/{id}/read")
    @Operation(summary = "Mark a chat message as read")
    public ResponseEntity<ChatMessageResponse> markAsRead(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(chatService.markAsRead(id, authentication));
    }

    @PostMapping("/api/v1/bookings/{bookingId}/messages")
    @Operation(summary = "Send a chat message in the context of a booking")
    public ResponseEntity<ChatMessageResponse> sendBookingMessage(
            @PathVariable UUID bookingId,
            @Valid @RequestBody ChatMessageRequest request,
            Authentication authentication) {
        ChatMessageRequest bookingRequest = new ChatMessageRequest(
                bookingId,
                request.recipientId(),
                request.message()
        );
        ChatMessageResponse response = chatService.sendMessage(bookingRequest, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/bookings/{bookingId}/messages")
    @Operation(summary = "Get chat messages for a specific booking")
    public ResponseEntity<List<ChatMessageResponse>> getBookingMessages(
            @PathVariable UUID bookingId,
            Authentication authentication) {
        return ResponseEntity.ok(chatService.getMessages(bookingId, null, authentication));
    }
}
