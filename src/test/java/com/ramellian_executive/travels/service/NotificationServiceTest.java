package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.AppNotification;
import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.model.NotificationResponse;
import com.ramellian_executive.travels.repository.AppNotificationRepository;
import com.ramellian_executive.travels.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private AppNotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private NotificationService notificationService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmailAddress("user@example.com");
    }

    @Test
    void createNotificationSavesNotification() {
        AppNotification saved = new AppNotification();
        saved.setId(UUID.randomUUID());
        saved.setRecipient(user);
        saved.setTitle("Title");
        saved.setMessage("Message");
        saved.setType("BOOKING_CREATED");
        UUID refId = UUID.randomUUID();
        saved.setReferenceId(refId);
        saved.setReferenceType("BOOKING");

        when(notificationRepository.save(any(AppNotification.class))).thenAnswer(inv -> inv.getArgument(0));

        AppNotification result = notificationService.createNotification(
                user, "Title", "Message", "BOOKING_CREATED", refId, "BOOKING");

        assertNotNull(result);
        assertEquals("Title", result.getTitle());
        assertEquals("Message", result.getMessage());
        assertEquals("BOOKING_CREATED", result.getType());
        assertEquals(refId, result.getReferenceId());
        assertFalse(result.isRead());
    }

    @Test
    void getNotificationsReturnsForUser() {
        when(authentication.getName()).thenReturn("user@example.com");
        when(userRepository.findByEmailAddressIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        AppNotification notif = new AppNotification();
        notif.setId(UUID.randomUUID());
        notif.setRecipient(user);
        notif.setTitle("Test");
        notif.setMessage("Msg");
        notif.setType("BOOKING_CREATED");

        when(notificationRepository.findByRecipientOrderByCreatedAtDesc(user)).thenReturn(List.of(notif));

        List<NotificationResponse> responses = notificationService.getNotifications(authentication, false);

        assertEquals(1, responses.size());
        assertEquals("Test", responses.get(0).title());
    }

    @Test
    void countUnreadReturnsCount() {
        when(authentication.getName()).thenReturn("user@example.com");
        when(userRepository.findByEmailAddressIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(notificationRepository.countByRecipientAndRead(user, false)).thenReturn(5L);

        long count = notificationService.countUnread(authentication);
        assertEquals(5L, count);
    }

    @Test
    void markAsReadUpdatesNotification() {
        UUID id = UUID.randomUUID();
        when(authentication.getName()).thenReturn("user@example.com");
        when(userRepository.findByEmailAddressIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        AppNotification notif = new AppNotification();
        notif.setId(id);
        notif.setRecipient(user);
        notif.setRead(false);

        when(notificationRepository.findByIdAndRecipient(id, user)).thenReturn(Optional.of(notif));
        when(notificationRepository.save(any(AppNotification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(id, authentication);

        assertTrue(response.read());
        assertNotNull(response.readAt());
    }

    @Test
    void markAllAsReadCallsRepository() {
        when(authentication.getName()).thenReturn("user@example.com");
        when(userRepository.findByEmailAddressIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        notificationService.markAllAsRead(authentication);

        verify(notificationRepository).markAllAsReadForRecipient(eq(user), any());
    }
}
