package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.BotSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BotSessionRepository extends JpaRepository<BotSession, UUID> {
    Optional<BotSession> findByChannelIdAndChannel(String channelId, String channel);
}
