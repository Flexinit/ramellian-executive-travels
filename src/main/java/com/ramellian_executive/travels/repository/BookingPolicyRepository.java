package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.BookingPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingPolicyRepository extends JpaRepository<BookingPolicy, UUID> {
    Optional<BookingPolicy> findFirstByOwnerIsNullOrderByCreatedAtDesc();
    Optional<BookingPolicy> findFirstByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
