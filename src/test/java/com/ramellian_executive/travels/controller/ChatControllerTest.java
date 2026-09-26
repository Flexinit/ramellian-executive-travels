package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.ChatMessageRequest;
import com.ramellian_executive.travels.model.ChatMessageResponse;
import com.ramellian_executive.travels.model.ConversationResponse;
import com.ramellian_executive.travels.service.ChatService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ChatController chatController;

    @Test
    void sendMessageDelegatesToService() {
        ChatMessageRequest request = new ChatMessageRequest(UUID.randomUUID(), null, "Hello");
        ChatMessageResponse messageResponse = new ChatMessageResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Renter", UUID.randomUUID(), "Owner",
                request.bookingId(), UUID.randomUUID(), "Vehicle", "Hello", false, null, ZonedDateTime.now());

        when(chatService.sendMessage(request, authentication)).thenReturn(messageResponse);

        ResponseEntity<ChatMessageResponse> response = chatController.sendMessage(request, authentication);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Hello", response.getBody().message());
    }

    @Test
    void getMessagesDelegatesToService() {
        UUID bookingId = UUID.randomUUID();
        when(chatService.getMessages(bookingId, null, authentication)).thenReturn(List.of());

        ResponseEntity<List<ChatMessageResponse>> response = chatController.getMessages(bookingId, null, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().size());
    }

    @Test
    void getConversationsDelegatesToService() {
        when(chatService.getConversations(authentication)).thenReturn(List.of());

        ResponseEntity<List<ConversationResponse>> response = chatController.getConversations(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().size());
    }

    @Test
    void markAsReadDelegatesToService() {
        UUID id = UUID.randomUUID();
        ChatMessageResponse messageResponse = new ChatMessageResponse(
                id, UUID.randomUUID(), "Renter", UUID.randomUUID(), "Owner",
                null, null, null, "Hello", true, ZonedDateTime.now(), ZonedDateTime.now());

        when(chatService.markAsRead(id, authentication)).thenReturn(messageResponse);

        ResponseEntity<ChatMessageResponse> response = chatController.markAsRead(id, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().read());
    }

    @Test
    void sendBookingMessageSetsBookingIdInRequest() {
        UUID bookingId = UUID.randomUUID();
        ChatMessageRequest request = new ChatMessageRequest(null, null, "Message on booking");
        ChatMessageResponse messageResponse = new ChatMessageResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Renter", UUID.randomUUID(), "Owner",
                bookingId, UUID.randomUUID(), "Vehicle", "Message on booking", false, null, ZonedDateTime.now());

        when(chatService.sendMessage(any(ChatMessageRequest.class), eq(authentication))).thenReturn(messageResponse);

        ResponseEntity<ChatMessageResponse> response = chatController.sendBookingMessage(bookingId, request, authentication);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("Message on booking", response.getBody().message());
    }
}
