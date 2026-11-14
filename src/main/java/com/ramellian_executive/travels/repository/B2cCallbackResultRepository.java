package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.B2cCallbackResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface B2cCallbackResultRepository extends JpaRepository<B2cCallbackResult, UUID> {

    List<B2cCallbackResult> findByDisbursementIdOrderByReceivedAtDesc(UUID disbursementId);

    List<B2cCallbackResult> findAllByOrderByReceivedAtDesc();
}
