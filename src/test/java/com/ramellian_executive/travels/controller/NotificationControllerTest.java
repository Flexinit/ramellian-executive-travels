package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.NotificationResponse;
import com.ramellian_executive.travels.service.NotificationService;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private NotificationController notificationController;

    @Test
    void getNotificationsReturnsList() {
        NotificationResponse notification = new NotificationResponse(
                UUID.randomUUID(), UUID.randomUUID(), "Title", "Message",
                "BOOKING_CREATED", UUID.randomUUID(), "BOOKING", false, null, ZonedDateTime.now());

        when(notificationService.getNotifications(authentication, false)).thenReturn(List.of(notification));

        ResponseEntity<List<NotificationResponse>> response = notificationController.getNotifications(authentication, false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Title", response.getBody().get(0).title());
    }

    @Test
    void getUnreadCountReturnsCount() {
        when(notificationService.countUnread(authentication)).thenReturn(3L);

        ResponseEntity<Map<String, Long>> response = notificationController.getUnreadCount(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3L, response.getBody().get("unreadCount"));
    }

    @Test
    void markAsReadReturnsUpdatedNotification() {
        UUID id = UUID.randomUUID();
        NotificationResponse notification = new NotificationResponse(
                id, UUID.randomUUID(), "Title", "Message",
                "BOOKING_CREATED", UUID.randomUUID(), "BOOKING", true, ZonedDateTime.now(), ZonedDateTime.now());

        when(notificationService.markAsRead(id, authentication)).thenReturn(notification);

        ResponseEntity<NotificationResponse> response = notificationController.markAsRead(id, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().read());
    }

    @Test
    void markAllAsReadCallsService() {
        ResponseEntity<Map<String, String>> response = notificationController.markAllAsRead(authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationService).markAllAsRead(authentication);
    }
}
