package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.AppNotification;
import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.model.NotificationResponse;
import com.ramellian_executive.travels.repository.AppNotificationRepository;
import com.ramellian_executive.travels.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final AppNotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public AppNotification createNotification(User recipient, String title, String message, String type,
                                              UUID referenceId, String referenceType) {
        if (recipient == null) {
            return null;
        }
        AppNotification notification = new AppNotification();
        notification.setRecipient(recipient);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setReferenceId(referenceId);
        notification.setReferenceType(referenceType);
        notification.setRead(false);
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Authentication authentication, Boolean unreadOnly) {
        User user = findUser(authentication);
        List<AppNotification> list;
        if (Boolean.TRUE.equals(unreadOnly)) {
            list = notificationRepository.findByRecipientAndReadOrderByCreatedAtDesc(user, false);
        } else {
            list = notificationRepository.findByRecipientOrderByCreatedAtDesc(user);
        }
        return list.stream().map(NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long countUnread(Authentication authentication) {
        User user = findUser(authentication);
        return notificationRepository.countByRecipientAndRead(user, false);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID notificationId, Authentication authentication) {
        User user = findUser(authentication);
        AppNotification notification = notificationRepository.findByIdAndRecipient(notificationId, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(ZonedDateTime.now());
            notification = notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllAsRead(Authentication authentication) {
        User user = findUser(authentication);
        notificationRepository.markAllAsReadForRecipient(user, ZonedDateTime.now());
    }

    private User findUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userRepository.findByEmailAddressIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
