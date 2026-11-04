package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository
        extends JpaRepository<PaymentTransaction, UUID>,
                JpaSpecificationExecutor<PaymentTransaction> {

    Optional<PaymentTransaction> findByPaymentId(UUID paymentId);

    Optional<PaymentTransaction> findByDisbursementId(UUID disbursementId);

    Optional<PaymentTransaction> findBySplitContributionId(UUID splitContributionId);
}
