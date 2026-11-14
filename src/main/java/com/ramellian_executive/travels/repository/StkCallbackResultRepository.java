package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.StkCallbackResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StkCallbackResultRepository extends JpaRepository<StkCallbackResult, UUID> {

    List<StkCallbackResult> findByPaymentIdOrderByReceivedAtDesc(UUID paymentId);

    List<StkCallbackResult> findByBookingIdOrderByReceivedAtDesc(UUID bookingId);

    List<StkCallbackResult> findAllByOrderByReceivedAtDesc();
}
