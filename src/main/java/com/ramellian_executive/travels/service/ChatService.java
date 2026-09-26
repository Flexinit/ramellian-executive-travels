package com.ramellian_executive.travels.service;

import com.ramellian_executive.travels.entity.Booking;
import com.ramellian_executive.travels.entity.ChatMessage;
import com.ramellian_executive.travels.entity.User;
import com.ramellian_executive.travels.entity.Vehicle;
import com.ramellian_executive.travels.model.ChatMessageRequest;
import com.ramellian_executive.travels.model.ChatMessageResponse;
import com.ramellian_executive.travels.model.ConversationResponse;
import com.ramellian_executive.travels.repository.BookingRepository;
import com.ramellian_executive.travels.repository.ChatMessageRepository;
import com.ramellian_executive.travels.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public ChatMessageResponse sendMessage(ChatMessageRequest request, Authentication authentication) {
        User sender = findUser(authentication);
        Booking booking = null;
        Vehicle vehicle = null;
        User recipient = null;

        if (request.bookingId() != null) {
            booking = bookingRepository.findById(request.bookingId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
            vehicle = booking.getVehicle();
            User owner = vehicle != null ? vehicle.getOwner() : null;
            User renter = booking.getUser();

            boolean isSenderRenter = renter != null && renter.getId().equals(sender.getId());
            boolean isSenderOwner = owner != null && owner.getId().equals(sender.getId());
            boolean isAdmin = isAdminOrSuperAdmin(authentication);

            if (!isSenderRenter && !isSenderOwner && !isAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to send messages for this booking");
            }

            if (request.recipientId() != null) {
                recipient = userRepository.findById(request.recipientId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient not found"));
            } else {
                if (isSenderRenter) {
                    recipient = owner;
                } else if (isSenderOwner) {
                    recipient = renter;
                }
            }

            if (recipient == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not resolve message recipient");
            }
        } else {
            if (request.recipientId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient ID is required when booking ID is not specified");
            }
            recipient = userRepository.findById(request.recipientId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient not found"));
        }

        if (sender.getId().equals(recipient.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot send message to yourself");
        }

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSender(sender);
        chatMessage.setRecipient(recipient);
        chatMessage.setBooking(booking);
        chatMessage.setVehicle(vehicle);
        chatMessage.setMessage(request.message().trim());
        chatMessage.setRead(false);

        ChatMessage saved = chatMessageRepository.save(chatMessage);

        if (notificationService != null) {
            String senderName = formatUserName(sender);
            String title = "New message from " + senderName;
            String preview = saved.getMessage().length() > 80
                    ? saved.getMessage().substring(0, 77) + "..."
                    : saved.getMessage();
            notificationService.createNotification(
                    recipient,
                    title,
                    preview,
                    "CHAT_MESSAGE",
                    saved.getId(),
                    "CHAT"
            );
        }

        return ChatMessageResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(UUID bookingId, UUID otherUserId, Authentication authentication) {
        User currentUser = findUser(authentication);

        if (bookingId != null) {
            Booking booking = bookingRepository.findById(bookingId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
            User owner = booking.getVehicle() != null ? booking.getVehicle().getOwner() : null;
            User renter = booking.getUser();
            boolean isRenter = renter != null && renter.getId().equals(currentUser.getId());
            boolean isOwner = owner != null && owner.getId().equals(currentUser.getId());
            boolean isAdmin = isAdminOrSuperAdmin(authentication);

            if (!isRenter && !isOwner && !isAdmin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to view messages for this booking");
            }

            return chatMessageRepository.findByBookingOrderByCreatedAtAsc(booking).stream()
                    .map(ChatMessageResponse::from)
                    .toList();
        }

        if (otherUserId != null) {
            User otherUser = userRepository.findById(otherUserId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            return chatMessageRepository.findDirectMessages(currentUser, otherUser).stream()
                    .map(ChatMessageResponse::from)
                    .toList();
        }

        return chatMessageRepository.findAllUserMessages(currentUser).stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversations(Authentication authentication) {
        User currentUser = findUser(authentication);
        List<ChatMessage> allMessages = chatMessageRepository.findAllUserMessages(currentUser);

        // Group by conversation key: bookingId (if present) + "_" + otherUserId
        Map<String, List<ChatMessage>> grouped = new LinkedHashMap<>();
        for (ChatMessage msg : allMessages) {
            User otherUser = msg.getSender().getId().equals(currentUser.getId()) ? msg.getRecipient() : msg.getSender();
            String key = (msg.getBooking() != null ? msg.getBooking().getId().toString() : "direct")
                    + ":" + otherUser.getId();
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(msg);
        }

        List<ConversationResponse> conversations = new ArrayList<>();
        for (List<ChatMessage> messages : grouped.values()) {
            if (messages.isEmpty()) continue;
            // First message in list is the newest (sorted by createdAt desc)
            ChatMessage latest = messages.get(0);
            User otherUser = latest.getSender().getId().equals(currentUser.getId()) ? latest.getRecipient() : latest.getSender();
            long unreadCount = messages.stream()
                    .filter(m -> m.getRecipient().getId().equals(currentUser.getId()) && !m.isRead())
                    .count();

            Vehicle vehicle = latest.getVehicle();
            if (vehicle == null && latest.getBooking() != null) {
                vehicle = latest.getBooking().getVehicle();
            }

            conversations.add(new ConversationResponse(
                    otherUser.getId(),
                    formatUserName(otherUser),
                    otherUser.getEmailAddress(),
                    latest.getBooking() != null ? latest.getBooking().getId() : null,
                    vehicle != null ? vehicle.getId() : null,
                    vehicle != null ? vehicle.getModel() : null,
                    latest.getMessage(),
                    latest.getCreatedAt(),
                    unreadCount
            ));
        }

        return conversations;
    }

    @Transactional
    public ChatMessageResponse markAsRead(UUID messageId, Authentication authentication) {
        User currentUser = findUser(authentication);
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        if (!message.getRecipient().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the recipient can mark a message as read");
        }

        if (!message.isRead()) {
            message.setRead(true);
            message.setReadAt(ZonedDateTime.now());
            message = chatMessageRepository.save(message);
        }

        return ChatMessageResponse.from(message);
    }

    private User findUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return userRepository.findByEmailAddressIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private boolean isAdminOrSuperAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_SUPER_ADMIN") || a.equals("BOOKING_MANAGE"));
    }

    private String formatUserName(User user) {
        if (user == null) {
            return "User";
        }
        String fName = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String lName = user.getLastName() != null ? user.getLastName().trim() : "";
        String fullName = (fName + " " + lName).trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }
        if (user.getEmailAddress() != null && !user.getEmailAddress().isBlank()) {
            return user.getEmailAddress().trim();
        }
        return "User";
    }
}
