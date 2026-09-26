package com.ramellian_executive.travels.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Entity
@Table(name = "app_notifications")
public class AppNotification extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", length = 2000, nullable = false)
    private String message;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "is_read", nullable = false, columnDefinition = "boolean default false")
    private boolean read;

    @Column(name = "read_at")
    private ZonedDateTime readAt;
}
