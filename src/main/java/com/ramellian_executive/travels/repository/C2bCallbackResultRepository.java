package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.C2bCallbackResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface C2bCallbackResultRepository extends JpaRepository<C2bCallbackResult, UUID> {

    List<C2bCallbackResult> findByPaymentIdOrderByReceivedAtDesc(UUID paymentId);

    List<C2bCallbackResult> findByBookingIdOrderByReceivedAtDesc(UUID bookingId);

    List<C2bCallbackResult> findAllByOrderByReceivedAtDesc();
}
