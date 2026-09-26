package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.Booking;
import com.ramellian_executive.travels.entity.ChatMessage;
import com.ramellian_executive.travels.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findByBookingOrderByCreatedAtAsc(Booking booking);

    @Query("SELECT m FROM ChatMessage m WHERE m.booking = :booking " +
            "ORDER BY m.createdAt ASC")
    List<ChatMessage> findMessagesByBooking(@Param("booking") Booking booking);

    @Query("SELECT m FROM ChatMessage m WHERE " +
            "((m.sender = :user1 AND m.recipient = :user2) OR (m.sender = :user2 AND m.recipient = :user1)) " +
            "ORDER BY m.createdAt ASC")
    List<ChatMessage> findDirectMessages(@Param("user1") User user1, @Param("user2") User user2);

    @Query("SELECT m FROM ChatMessage m WHERE m.booking = :booking AND " +
            "((m.sender = :user1 AND m.recipient = :user2) OR (m.sender = :user2 AND m.recipient = :user1)) " +
            "ORDER BY m.createdAt ASC")
    List<ChatMessage> findBookingMessagesBetweenUsers(
            @Param("booking") Booking booking, @Param("user1") User user1, @Param("user2") User user2);

    @Query("SELECT m FROM ChatMessage m WHERE m.sender = :user OR m.recipient = :user ORDER BY m.createdAt DESC")
    List<ChatMessage> findAllUserMessages(@Param("user") User user);

    long countByRecipientAndRead(User recipient, boolean read);
}
