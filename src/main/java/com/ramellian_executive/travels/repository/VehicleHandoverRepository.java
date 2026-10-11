package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.HandoverType;
import com.ramellian_executive.travels.entity.VehicleHandover;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleHandoverRepository extends JpaRepository<VehicleHandover, UUID> {
    Optional<VehicleHandover> findByBookingIdAndType(UUID bookingId, HandoverType type);
    List<VehicleHandover> findByBookingIdOrderByHandoverTimeAsc(UUID bookingId);
    boolean existsByBookingIdAndType(UUID bookingId, HandoverType type);
}
