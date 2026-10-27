package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.SplitPaymentContribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SplitPaymentContributionRepository extends JpaRepository<SplitPaymentContribution, UUID> {
    List<SplitPaymentContribution> findBySplitGroupId(UUID splitGroupId);
    Optional<SplitPaymentContribution> findByProviderOrderTrackingId(String providerOrderTrackingId);
}
