package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.AppNotification;
import com.ramellian_executive.travels.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppNotificationRepository extends JpaRepository<AppNotification, UUID> {

    List<AppNotification> findByRecipientOrderByCreatedAtDesc(User recipient);

    List<AppNotification> findByRecipientAndReadOrderByCreatedAtDesc(User recipient, boolean read);

    long countByRecipientAndRead(User recipient, boolean read);

    Optional<AppNotification> findByIdAndRecipient(UUID id, User recipient);

    @Modifying
    @Query("UPDATE AppNotification n SET n.read = true, n.readAt = :now WHERE n.recipient = :recipient AND n.read = false")
    int markAllAsReadForRecipient(@Param("recipient") User recipient, @Param("now") ZonedDateTime now);
}
