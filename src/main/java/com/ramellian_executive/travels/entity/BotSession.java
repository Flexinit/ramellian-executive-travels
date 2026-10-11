package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "bot_session", indexes = {
        @Index(name = "idx_bot_session_channel", columnList = "channel_id, channel")
})
@Getter
@Setter
public class BotSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "channel_id", nullable = false)
    private String channelId;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "messages_json", columnDefinition = "text")
    private String messagesJson = "[]";

    @Column(name = "user_email")
    private String userEmail;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "pending_booking_id")
    private UUID pendingBookingId;

    @Column(name = "pending_contract_id")
    private UUID pendingContractId;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt = ZonedDateTime.now();

    @Column(name = "expires_at")
    private ZonedDateTime expiresAt = ZonedDateTime.now().plusHours(24);
}
