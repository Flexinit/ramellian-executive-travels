package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.Disbursement;
import com.ramellian_executive.travels.entity.DisbursementStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisbursementRepository extends JpaRepository<Disbursement, UUID> {

    List<Disbursement> findAllByOrderByCreatedAtDesc();

    List<Disbursement> findByStatusOrderByCreatedAtDesc(DisbursementStatus status);

    Optional<Disbursement> findByPaymentId(UUID paymentId);

    boolean existsByPaymentId(UUID paymentId);
}
