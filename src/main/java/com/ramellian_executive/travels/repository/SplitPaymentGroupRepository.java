package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.SplitPaymentGroup;
import com.ramellian_executive.travels.entity.SplitPaymentGroupStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SplitPaymentGroupRepository extends JpaRepository<SplitPaymentGroup, UUID> {
    Optional<SplitPaymentGroup> findByBookingIdAndStatusIn(UUID bookingId, List<SplitPaymentGroupStatus> statuses);
    List<SplitPaymentGroup> findByStatusAndExpiresAtBefore(SplitPaymentGroupStatus status, ZonedDateTime now);
}
