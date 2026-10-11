package com.ramellian_executive.travels.repository;

import com.ramellian_executive.travels.entity.Booking;
import com.ramellian_executive.travels.entity.Review;
import com.ramellian_executive.travels.entity.ReviewType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {
    boolean existsByBookingAndReviewType(Booking booking, ReviewType reviewType);
    @Query("select r from Review r join fetch r.booking b join fetch b.vehicle v join fetch b.user u where v.id = :vehicleId and r.reviewType = 'VEHICLE'")
    List<Review> findAllByVehicleId(@Param("vehicleId") UUID vehicleId);
    @Query("select r from Review r join fetch r.booking b join fetch b.vehicle v join fetch b.user u where v.owner.id = :ownerId and r.reviewType = 'VEHICLE'")
    List<Review> findAllByVehicleOwnerId(@Param("ownerId") UUID ownerId);
    @Query("select avg(r.rating) from Review r where r.booking.vehicle.id = :vehicleId and r.reviewType = 'VEHICLE'")
    Double averageRatingByVehicle(@Param("vehicleId") UUID vehicleId);
    @Query("select r from Review r join fetch r.booking b join fetch b.vehicle v join fetch b.user u where b.user.id = :renterId and r.reviewType = 'RENTER'")
    List<Review> findAllRenterReviewsByRenterId(@Param("renterId") UUID renterId);
    Optional<Review> findByBookingAndReviewType(Booking booking, ReviewType reviewType);
}
