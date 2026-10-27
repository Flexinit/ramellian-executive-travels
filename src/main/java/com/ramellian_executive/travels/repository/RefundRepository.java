package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.Refund;
import com.ramellian_executive.travels.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    boolean existsByPaymentId(UUID paymentId);
    Optional<Refund> findByPaymentId(UUID paymentId);
    List<Refund> findByStatusOrderByCreatedAtDesc(RefundStatus status);
    List<Refund> findAllByOrderByCreatedAtDesc();
}
