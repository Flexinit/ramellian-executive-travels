package com.ramellian_executive.travels.controller;

import com.ramellian_executive.travels.model.NotificationResponse;
import com.ramellian_executive.travels.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications management")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get current user notifications")
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            Authentication authentication,
            @RequestParam(required = false) Boolean unreadOnly) {
        return ResponseEntity.ok(notificationService.getNotifications(authentication, unreadOnly));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get count of unread notifications")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        long count = notificationService.countUnread(authentication);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(notificationService.markAsRead(id, authentication));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<Map<String, String>> markAllAsRead(Authentication authentication) {
        notificationService.markAllAsRead(authentication);
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read"));
    }
}
