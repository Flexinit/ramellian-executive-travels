package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.Disbursement;
import com.ramellian_executive.travels.entity.DisbursementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisbursementRepository extends JpaRepository<Disbursement, UUID> {

    List<Disbursement> findAllByOrderByCreatedAtDesc();

    List<Disbursement> findByStatusOrderByCreatedAtDesc(DisbursementStatus status);

    Optional<Disbursement> findByPaymentId(UUID paymentId);
    List<Disbursement> findAllByPaymentIdIn(List<UUID> paymentIds);

    boolean existsByPaymentId(UUID paymentId);

    List<Disbursement> findByOwnerEmailIgnoreCaseOrderByCreatedAtDesc(String ownerEmail);

    @Query("SELECT COALESCE(SUM(d.amount), 0.0) FROM Disbursement d WHERE d.status = :status")
    double sumByStatus(@Param("status") DisbursementStatus status);
}
