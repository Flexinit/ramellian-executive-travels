package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.PesapalEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PesapalEventRepository extends JpaRepository<PesapalEvent, UUID> {
    boolean existsByOrderTrackingIdAndEventType(String orderTrackingId, String eventType);
}
