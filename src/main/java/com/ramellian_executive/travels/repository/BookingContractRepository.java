package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.BookingContract;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BookingContractRepository extends JpaRepository<BookingContract, UUID> {
    Optional<BookingContract> findByBookingId(UUID bookingId);
    boolean existsByBookingId(UUID bookingId);
}
