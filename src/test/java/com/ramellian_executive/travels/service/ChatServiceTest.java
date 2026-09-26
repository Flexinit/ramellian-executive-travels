package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.*;
import com.ramellian_executive.travels.model.ChatMessageRequest;
import com.ramellian_executive.travels.model.ChatMessageResponse;
import com.ramellian_executive.travels.model.ConversationResponse;
import com.ramellian_executive.travels.repository.BookingRepository;
import com.ramellian_executive.travels.repository.ChatMessageRepository;
import com.ramellian_executive.travels.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ChatService chatService;

    private User renter;
    private User owner;
    private Vehicle vehicle;
    private Booking booking;

    @BeforeEach
    void setUp() {
        renter = new User();
        renter.setId(UUID.randomUUID());
        renter.setEmailAddress("renter@example.com");
        renter.setFirstName("John");
        renter.setLastName("Renter");

        owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setEmailAddress("owner@example.com");
        owner.setFirstName("Jane");
        owner.setLastName("Owner");

        vehicle = new Vehicle();
        vehicle.setId(UUID.randomUUID());
        vehicle.setModel("Toyota Prado");
        vehicle.setOwner(owner);

        booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setUser(renter);
        booking.setVehicle(vehicle);
    }

    @Test
    void renterSendsMessageToOwnerInBookingContext() {
        when(authentication.getName()).thenReturn(renter.getEmailAddress());
        when(userRepository.findByEmailAddressIgnoreCase(renter.getEmailAddress())).thenReturn(Optional.of(renter));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            msg.setId(UUID.randomUUID());
            return msg;
        });

        ChatMessageRequest request = new ChatMessageRequest(booking.getId(), null, "Hello, is the car ready?");
        ChatMessageResponse response = chatService.sendMessage(request, authentication);

        assertNotNull(response);
        assertEquals("Hello, is the car ready?", response.message());
        assertEquals(renter.getId(), response.senderId());
        assertEquals(owner.getId(), response.recipientId());
        assertEquals(booking.getId(), response.bookingId());
        assertEquals("Toyota Prado", response.vehicleModel());

        verify(notificationService).createNotification(
                eq(owner),
                contains("John Renter"),
                eq("Hello, is the car ready?"),
                eq("CHAT_MESSAGE"),
                any(),
                eq("CHAT")
        );
    }

    @Test
    void ownerSendsMessageToRenterInBookingContext() {
        when(authentication.getName()).thenReturn(owner.getEmailAddress());
        when(userRepository.findByEmailAddressIgnoreCase(owner.getEmailAddress())).thenReturn(Optional.of(owner));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage msg = inv.getArgument(0);
            msg.setId(UUID.randomUUID());
            return msg;
        });

        ChatMessageRequest request = new ChatMessageRequest(booking.getId(), null, "Yes, the vehicle is ready for pickup!");
        ChatMessageResponse response = chatService.sendMessage(request, authentication);

        assertNotNull(response);
        assertEquals("Yes, the vehicle is ready for pickup!", response.message());
        assertEquals(owner.getId(), response.senderId());
        assertEquals(renter.getId(), response.recipientId());

        verify(notificationService).createNotification(
                eq(renter),
                contains("Jane Owner"),
                eq("Yes, the vehicle is ready for pickup!"),
                eq("CHAT_MESSAGE"),
                any(),
                eq("CHAT")
        );
    }

    @Test
    void unauthorizedUserCannotSendMessageOnBooking() {
        User thirdParty = new User();
        thirdParty.setId(UUID.randomUUID());
        thirdParty.setEmailAddress("third@example.com");

        when(authentication.getName()).thenReturn(thirdParty.getEmailAddress());
        when(userRepository.findByEmailAddressIgnoreCase(thirdParty.getEmailAddress())).thenReturn(Optional.of(thirdParty));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        ChatMessageRequest request = new ChatMessageRequest(booking.getId(), null, "Sneak message");

        assertThrows(ResponseStatusException.class, () -> chatService.sendMessage(request, authentication));
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void getMessagesForBookingReturnsMessages() {
        when(authentication.getName()).thenReturn(renter.getEmailAddress());
        when(userRepository.findByEmailAddressIgnoreCase(renter.getEmailAddress())).thenReturn(Optional.of(renter));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        ChatMessage msg1 = new ChatMessage();
        msg1.setId(UUID.randomUUID());
        msg1.setSender(renter);
        msg1.setRecipient(owner);
        msg1.setBooking(booking);
        msg1.setMessage("Hello");

        when(chatMessageRepository.findByBookingOrderByCreatedAtAsc(booking)).thenReturn(List.of(msg1));

        List<ChatMessageResponse> messages = chatService.getMessages(booking.getId(), null, authentication);

        assertEquals(1, messages.size());
        assertEquals("Hello", messages.get(0).message());
    }

    @Test
    void getConversationsGroupsAndSummarizes() {
        when(authentication.getName()).thenReturn(renter.getEmailAddress());
        when(userRepository.findByEmailAddressIgnoreCase(renter.getEmailAddress())).thenReturn(Optional.of(renter));

        ChatMessage msg1 = new ChatMessage();
        msg1.setId(UUID.randomUUID());
        msg1.setSender(owner);
        msg1.setRecipient(renter);
        msg1.setBooking(booking);
        msg1.setVehicle(vehicle);
        msg1.setMessage("See you soon");
        msg1.setRead(false);
        msg1.setCreatedAt(ZonedDateTime.now());

        when(chatMessageRepository.findAllUserMessages(renter)).thenReturn(List.of(msg1));

        List<ConversationResponse> convos = chatService.getConversations(authentication);

        assertEquals(1, convos.size());
        assertEquals("See you soon", convos.get(0).lastMessage());
        assertEquals(1, convos.get(0).unreadCount());
        assertEquals(owner.getId(), convos.get(0).otherUserId());
    }

    @Test
    void markAsReadMarksMessageRead() {
        UUID msgId = UUID.randomUUID();
        when(authentication.getName()).thenReturn(renter.getEmailAddress());
        when(userRepository.findByEmailAddressIgnoreCase(renter.getEmailAddress())).thenReturn(Optional.of(renter));

        ChatMessage msg = new ChatMessage();
        msg.setId(msgId);
        msg.setSender(owner);
        msg.setRecipient(renter);
        msg.setRead(false);

        when(chatMessageRepository.findById(msgId)).thenReturn(Optional.of(msg));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        ChatMessageResponse response = chatService.markAsRead(msgId, authentication);

        assertTrue(response.read());
        assertNotNull(response.readAt());
    }
}
