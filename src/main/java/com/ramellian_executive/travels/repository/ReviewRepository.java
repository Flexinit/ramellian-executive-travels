package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.Booking;
import com.ramellian_executive.travels.entity.Review;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {
    boolean existsByBooking(Booking booking);
    @Query("select r from Review r join fetch r.booking b join fetch b.vehicle v join fetch b.user u where v.id = :vehicleId")
    List<Review> findAllByVehicleId(@Param("vehicleId") UUID vehicleId);
    @Query("select avg(r.rating) from Review r where r.booking.vehicle.id = :vehicleId")
    Double averageRatingByVehicle(@Param("vehicleId") UUID vehicleId);
}
